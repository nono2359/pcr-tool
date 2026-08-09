package cn.wthee.pcrtool.data.db.view.skilltype

import cn.wthee.pcrtool.R
import cn.wthee.pcrtool.data.db.view.SkillActionDetail
import cn.wthee.pcrtool.data.enums.SkillActionType
import cn.wthee.pcrtool.utils.getString
import cn.wthee.pcrtool.utils.getTarget
import cn.wthee.pcrtool.utils.getTimeText
import cn.wthee.pcrtool.utils.getValueText

// 8：行动速度变更、83：可叠加行动速度变更、99：范围速度变更
fun SkillActionDetail.speed(): String {
    //判断异常状态
    tag = getString(
        when (actionDetail1) {
            1 -> R.string.skill_ailment_1
            2 -> R.string.skill_ailment_2
            3 -> R.string.skill_ailment_3
            4 -> R.string.skill_ailment_4
            5 -> R.string.skill_ailment_5
            6 -> R.string.skill_ailment_6
            7, 12, 14 -> R.string.skill_ailment_7_12_14
            8 -> R.string.skill_ailment_8
            9 -> R.string.skill_ailment_9
            10 -> R.string.skill_ailment_10
            11 -> R.string.skill_ailment_11
            13 -> R.string.skill_ailment_13
            else -> R.string.unknown
        }
    )
    val value = getValueText(1, actionValue1, actionValue2)
    val time = getTimeText(3, actionValue3, actionValue4)

    //额外、范围速度变更
    when (actionType) {
        SkillActionType.SUPERIMPOSE_CHANGE_ACTION_SPEED.type -> {
            tag += getString(R.string.skill_ailment_extra)
        }

        SkillActionType.SPEED_FIELD.type -> {
            tag += getString(R.string.skill_ailment_field)
        }
    }

    return when (actionDetail1) {
        1, 2 -> {
            val descText =
                if (actionType == SkillActionType.SUPERIMPOSE_CHANGE_ACTION_SPEED.type) {
                    val type = getString(
                        if (actionDetail1 == 1) {
                            R.string.skill_reduce
                        } else {
                            R.string.skill_increase
                        }
                    )
                    getString(R.string.skill_action_speed_change, type, value)
                } else {
                    getString(R.string.skill_action_speed_multiple, value)
                }
            if (actionType == SkillActionType.SPEED_FIELD.type) {
                getString(
                    R.string.skill_action_type_desc_field,
                    actionValue5.toInt(),
                    descText,
                    time
                )
            } else {
                "${getTarget()}の$tag、$descText$time"
            }
        }

        else -> {
            val count = if (actionDetail2 == 1) {
                getString(R.string.skill_action_hit_remove)
            } else {
                ""
            }
            getString(R.string.skill_action_type_desc_8, getTarget(), tag, time, count)
        }
    }
}

// 100：免疫无法行动的异常状态
fun SkillActionDetail.ignoreSpeedDown(): String {
    val time = getTimeText(3, actionValue3)
    val limit = if (actionValue1.toInt() == -1) {
        getString(R.string.none)
    } else {
        getString(R.string.skill_action_type_desc_100_count, actionValue1.toInt())
    }
    return getString(
        R.string.skill_action_type_desc_100,
        getTarget(),
        limit,
        time
    )
}
