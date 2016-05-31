// An Objective requiring the player to travel to another Town, and will be marked
// complete when the player gets there.

import shandalike.Util
import shandalike.UIModel
import shandalike.Callback
import shandalike.data.behavior.Behavior
import shandalike.data.behavior.IBehavioral
import shandalike.data.reward.Reward
import shandalike.data.reward.CardReward
import lib.QuestController
import lib.RewardController
import lib.Encounters
import groovy.transform.Field

// Every behavior must implement methodMissing.
def methodMissing(String name, args) { null }

def getEncounterById(String id) {
  Encounters encs = Util.runScript("encounters", "getEncounters")
  encs.getById(id)
}

// Get description of this objective
String getLongDescription(Behavior objective) {
  def targets = objective.getVar("targets")
  String dest = objective.getVar("destinationName")
  String rst = "<b>Defeat the following enemies:</b><br>\n"
  targets.each { k, v ->
    def encounter = getEncounterById(k)
    int nKilled = v[0]
    int nNeeded = v[1]
    if(encounter) {
      rst += "- ${encounter.name} (${nKilled}/${nNeeded})<br>\n"
    }
  }
  rst += "Then return to ${dest}.<br>"
  rst
}

String getObjectiveDescription(Behavior objective, IBehavioral objectives, arg1, arg2) {
  String ldesc = getLongDescription(objective)
  def rdesc = objective.getVar("rewards")
  String rinfo = RewardController.describeRewardsFromDescriptors(rdesc)
  "${ldesc}<br><b>Reward:</b><br>\n${rinfo}"
}

String getTitle(behavior) {
  "Hidden objective_kill buff"
}

String getDescription(behavior) {
  "Tracks kill objectives"
}

boolean isHidden(behavior) {
  true
}

boolean isHelpful(behavior) {
  true
}

// Can this quest be abandoned?
boolean canAbandon(behavior, behavioral, arg1, arg2) {
  true
}

// Is this quest complete?
boolean isComplete(behavior, behavioral, arg1, arg2) {
  if(behavior.getVar("isComplete")) {
    true
  } else {
    false
  }
}

// Quest must be complete and player must be in destination city
// NOTE: canTurnIn is executed in the quest context, not the playerbuff context
boolean canTurnIn(behavior, behavioral, arg1, arg2) {
  if(!isComplete(behavior, behavioral, arg1, arg2)) return false
  String inTownId = Util.getActiveMapState().inTownId
  if(!inTownId) return false
  if(!inTownId.equals(behavior.getVar("destinationId"))) return false
  return true
}

// Upon removal, dispell the tracking debuff from the player
void behaviorWillRemove(behavior, behavioral, arg1, arg2) {
  if(behavioral == Util.getPlayer().getObjectives()) {
    // Remove tracking debuff which has the same tag as this debuff
    Behavior.purgeByTag(Util.getPlayer(), behavior.tag)
  }
}

// Upon addition, add a tracking buff to the player that will trigger
// when he reaches the destination
void behaviorDidAdd(behavior, behavioral, arg1, arg2) {
  // This script serves a "dual use" as both the objective and tracking buff.
  // Only run this routine when the objective is being added to the objectives.
  if(behavioral == Util.getPlayer().getObjectives()) {
    // Assign a tag for later reference
    behavior.tag = Util.generateID()
    // Assign a buff to the player with this script.
    Behavior playerBuff = new Behavior()
    playerBuff.script = "objective_kill"
    playerBuff.tag = behavior.tag
    Util.getPlayer().addBehavior(playerBuff)
  }
}

// void playerDidEnterTown(behavior, behavioral, town, townMenu) {
//   Behavior obj = QuestController.getObjectiveForTag(behavior.tag)
//   String destId = obj.getVar("destinationId")
//   if(town.id.equals(destId)) {
//     obj.setVar("isComplete", true)
//   }
// }

// void townWillBuildMenu(behavior, player, town, townMenu) {
//   Behavior obj = QuestController.getObjectiveForTag(behavior.tag)
//   String destId = obj.getVar("destinationId")
//   if(town.id.equals(destId) && obj.getVar("isComplete")) {
//     buildUI(obj, Util.getPlayer().getObjectives(), townMenu, "complete")
//   }
// }

void checkComplete(objective) {
  def targets = objective.getVar("targets")
  boolean abort = false
  targets.each { k, v ->
    if(v[0] < v[1]) abort = true
  }
  if(abort) return
  println "objective_kill completed: ${targets}"
  objective.setVar("isComplete", true)
}

void duelEnded(trackingBuff, player, duel, duelResult) {
  // Get encounter id
  String encId = duel.encounterId
  println "objective_kill.duelEnded with encounter id ${encId}, result ${duelResult}, win ${duelResult.playerDidWin}"
  if(encId == null) return
  // Only count wins
  if(!duelResult.playerDidWin) return
  // Get actual objective
  Behavior objective = QuestController.getObjectiveForTag(trackingBuff.tag)
  // Notch up the kill value on this encounter
  def targets = objective.getVar("targets")
  println "objective_kill.duelEnded targets ${targets} encid ${encId} targets[encid] ${targets[encId]} try 2 ${targets.get(encId)}"
  if(targets[encId] != null) {
    targets[encId][0] = targets[encId][0] + 1
    println "objective_kill.duelEnded targets updated ${targets}"
    // Check for completeion
    checkComplete(objective)
  }
}
