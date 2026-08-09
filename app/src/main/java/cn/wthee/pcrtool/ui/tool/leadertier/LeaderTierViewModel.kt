package cn.wthee.pcrtool.ui.tool.leadertier

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.wthee.pcrtool.data.db.repository.UnitRepository
import cn.wthee.pcrtool.data.enums.LeaderTierType
import cn.wthee.pcrtool.data.enums.TalentType
import cn.wthee.pcrtool.data.model.LeaderTierData
import cn.wthee.pcrtool.data.model.LeaderTierGroup
import cn.wthee.pcrtool.data.model.ResponseData
import cn.wthee.pcrtool.data.network.ApiRepository
import cn.wthee.pcrtool.ui.LoadState
import cn.wthee.pcrtool.utils.fixedLeaderDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


/**
 * 页面状态：角色梯队
 */
@Immutable
data class LeaderTierUiState(
    val currentGroupList: List<LeaderTierGroup> = arrayListOf(),
    val count: Int = 0,
    val date: String = "",
    //角色梯队类型
    val leaderTierType: LeaderTierType = LeaderTierType.ALL,
    val leaderTierMap: HashMap<Int, ResponseData<LeaderTierData>> = hashMapOf(),
    val openDialog: Boolean = false,
    val loadState: LoadState = LoadState.Loading,

    //天赋筛选相关
    val talentType: TalentType = TalentType.ALL,
    val talentUnitMap: HashMap<Int, ArrayList<Int>> = hashMapOf(),
    val openTalentDialog: Boolean = false,

    )

/**
 * 角色梯队 ViewModel
 *
 * @param apiRepository
 */
@HiltViewModel
class LeaderTierViewModel @Inject constructor(
    private val apiRepository: ApiRepository,
    private val unitRepository: UnitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderTierUiState())
    val uiState: StateFlow<LeaderTierUiState> = _uiState.asStateFlow()

    init {
        getTalentUnitMap()
        getLeaderTier(LeaderTierType.ALL.type, TalentType.ALL.type)
    }

    /**
     * 获取角色基本信息
     *
     * @param unitId 角色编号
     */
    fun getCharacterInfo(unitId: Int) = flow {
        emit(unitRepository.getCharacterInfo(unitId))
    }

    /**
     * 获取角色梯队
     */
    private fun getLeaderTier(
        type: Int = _uiState.value.leaderTierType.type,
        talentType: Int = _uiState.value.talentType.type
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loadState = LoadState.Loading
                )
            }

            val leaderTierMap = _uiState.value.leaderTierMap
            if (leaderTierMap[type] == null) {
                leaderTierMap[type] = apiRepository.getLeaderTier(type)
            }

            //角色id（按天赋筛选）
            val unitIdList = _uiState.value.talentUnitMap[talentType] ?: arrayListOf()

            //分组
            val groupList = arrayListOf<LeaderTierGroup>()
            var count = 0
            leaderTierMap[type]?.data?.let { data ->
                data.leader.forEach { apiLeaderItem ->
                    val leaderItem = supplementUnitId(apiLeaderItem)
                    var group = groupList.find {
                        it.tier == leaderItem.tier
                    }
                    if (group == null) {
                        val descInfo = data.tierSummary.find {
                            it.tier == leaderItem.tier
                        }
                        group =
                            LeaderTierGroup(
                                leaderItem.tier,
                                arrayListOf(),
                                localizeTierDescription(descInfo?.desc.orEmpty())
                            )
                        groupList.add(group)
                    }
                    if (unitIdList.isNotEmpty()) {
                        //天赋筛选
                        if (unitIdList.contains(leaderItem.unitId)) {
                            group.leaderList.add(leaderItem)
                            count++
                        }
                    } else {
                        group.leaderList.add(leaderItem)
                        count++
                    }
                }
            }



            _uiState.update {
                it.copy(
                    currentGroupList = groupList,
                    count = count,
                    date = leaderTierMap[type]?.data?.desc?.fixedLeaderDate ?: "",
                    leaderTierMap = leaderTierMap,
                    leaderTierType = LeaderTierType.getByValue(type),
                    talentType = TalentType.getByType(talentType),
                    loadState = it.loadState.isSuccess(leaderTierMap[type]?.data != null)
                )
            }
        }
    }

    /**
     * 切换类型
     */
    fun changeSelect(type: Int) {
        getLeaderTier(type = type)
    }

    /**
     * 弹窗状态更新
     */
    fun changeDialog(openDialog: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    openDialog = openDialog,
                    openTalentDialog = false
                )
            }
        }
    }

    /**
     * 切换天赋类型
     */
    fun changeTalentSelect(type: Int) {
        getLeaderTier(talentType = type)
    }

    /**
     * 弹窗状态更新
     */
    fun changeTalentDialog(openDialog: Boolean) {
        _uiState.update {
            it.copy(
                openTalentDialog = openDialog,
                openDialog = false
            )
        }
    }

    /**
     * 获取角色id按天赋分组
     */
    private fun getTalentUnitMap() {
        viewModelScope.launch {
            val map = unitRepository.getTalentUnitMap()
            _uiState.update {
                it.copy(
                    talentUnitMap = map
                )
            }
        }
    }
    /**
     * ランキングAPIにunitIdがない新キャラだけ、日本版DBのIDで補完する。
     * API側にunitIdが追加された場合はAPIの値を優先する。
     */
    private fun supplementUnitId(item: cn.wthee.pcrtool.data.model.LeaderTierItem): cn.wthee.pcrtool.data.model.LeaderTierItem {
        if (item.unitId != null && item.unitId != 0) return item

        val articleId = item.url.substringAfterLast('/').substringBefore('?').toIntOrNull()
        val unitId = articleId?.let(leaderTierUnitIdOverrides::get) ?: return item
        return item.copy(unitId = unitId)
    }

    private val leaderTierUnitIdOverrides = mapOf(
        543142 to 138301, // ペコリーヌ（アストライア）
        545207 to 138701, // ワカナ（ウィンター）
        545208 to 138801, // シオリ（ウィンター）
        551916 to 138901, // グレイス（バニー）
        555344 to 139001, // キョウカ（ゴシック）
        556609 to 139101, // キャル（覇瞳天星）
        558252 to 139201, // ミホ（ガルパン）
        558253 to 139401, // エリカ（ガルパン）
        558258 to 139301, // マホ（ガルパン）
        561397 to 136901, // ルルィ
        561881 to 139501, // リリ（ヴァルキュリア）
        562438 to 139701, // プレシア（ヴァルキュリア）
        562439 to 139601, // クリア（ヴァルキュリア）
        565564 to 139801, // シェフィ（ヴァードラッヘ）
        565758 to 139901, // ルイズマリー（サマー）
        566666 to 140001, // クレジッタ（サマー）
        570354 to 140101, // フブキ（サマー）
    )
    /** APIから返される中国語のTier説明を日本語表示に変換する。 */
    private fun localizeTierDescription(description: String): String = when (description) {
        "综合强度高" -> "総合評価が高い"
        "综合强度较高" -> "総合評価が比較的高い"
        "部分场景必要" -> "一部の場面で必須"
        "部分场景最佳" -> "一部の場面で最適"
        "部分场景较佳" -> "一部の場面で優秀"
        "部分场景可用" -> "一部の場面で活躍"
        "养成优先级低" -> "育成優先度が低い"
        "进攻阵容常用" -> "攻撃編成でよく使う"
        "防守阵容常用" -> "防衛編成でよく使う"
        "功能性强" -> "特定の役割で優秀"
        "部分技能强力" -> "一部のスキルが強力"
        "通用性较高" -> "汎用性が比較的高い"
        "通用性低" -> "汎用性が低い"
        else -> description
    }
}
