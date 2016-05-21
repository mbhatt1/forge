import shandalike.Util
import shandalike.data.world.MapState
import shandalike.data.entity.Entity
import shandalike.data.entity.MobilePawn
import shandalike.data.entity.PlayerPawn
import groovy.transform.Field

@Field MapState mapState
@Field PlayerPawn playerPawn
@Field String trashTileTerrain
@Field float x
@Field float y
@Field boolean failed
// The template the trash will be spawned from
@Field String trashTemplate

// Radius around player that trash is eligible to spawn in
@Field float spawnRadius = 20.0f
@Field def trashByColor = [
	"green": "green_trash_1",
	"white": "white_trash_1",
	"red": "red_trash_1",
	"blue": "blue_trash_1",
	"black": "black_trash_1"
]
// The chance of trash from a different terrain appearing instead of terrain-appropriate trash
@Field float wandererChance = 0.25f

void positionTrash(int tries) {
	// Prevent pathology.
	if(tries > 50) {
		failed = true
		return
	}
	// Preferentially spawn trash in the direction the player is walking
	x = playerPawn.pos.x + playerPawn.velocity.x * 3.0f;
	y = playerPawn.pos.y + playerPawn.velocity.y * 3.0f;
	// Locate random position for trash spawning
	x = x + (Util.randomFloat() - 0.5f) * 2.0f * spawnRadius
	y = y + (Util.randomFloat() - 0.5f) * 2.0f * spawnRadius
	// Don't spawn too close
	if(playerPawn.distanceFrom(x,y) < 4) {
		positionTrash(tries + 1)
		return
	}
	// Get terrain of trash. Don't spawn on no terrain
	trashTileTerrain = mapState.getMapInfo().getTerrainAt(x,y)
	if(!trashTileTerrain || trashTileTerrain.equals("none")) {
		positionTrash(tries + 1)
		return
	}
}

String getTrashTemplateForTerrain(String terrain) {
	return trashByColor[terrain]
}

void getTrashTemplate() {
	// Get template we should spawn
	String terrain = trashTileTerrain
	// Random chance: terrain varies from the one given
	if(Util.randomFloat() < wandererChance) {
		terrain = Util.pick(trashByColor.keySet(), Util.randomInt(trashByColor.keySet().size()))
	}
	// Get template from terrain and difficutly level info
	trashTemplate = getTrashTemplateForTerrain(terrain)
}

void reifyTrash() {
	trashTemplate = null
	getTrashTemplate()
	if(trashTemplate == null) {
		println "[Shandalike] No trash template found for terrain " + trashTileTerrain
		failed = true
		return
	}
	Entity ent = Util.getWorldState().getBaseWorld().entityTemplates.get(trashTemplate)
	if(ent == null) {
		println "[Shandalike] Invalid entity template ${trashTemplate}"
		failed = true
		return
	}
	// Copy the template
	MobilePawn pawn = (MobilePawn)ent.deepCopy()
	pawn.pos.x = x; pawn.pos.y = y
	pawn.setVar("trash", true)
	pawn.setVar("despawnAt", Util.getGameTime() + 60.0f)
	// Spawn pawn on map
	pawn.load()
	mapState.addEntity(pawn)
}

void makeTrash() {
	failed = false
	positionTrash(0)
	if(failed) return
	reifyTrash()
}

// Make more trash mobs
void spawnMoreTrash(trash) {
	for(int i=trash.size(); i < 10; i++) {
		println("[Shandalike] Spawning trash #${i}")
		makeTrash()
	}
}

// Cleanup faraway and expired trash mobs.
void cleanupTrash(trash) {
	// Despawn all those that are too far away
	def deadTrash = trash.findAll { it.distanceFrom(playerPawn) > (spawnRadius * 1.5f) }
	deadTrash.each {
		println "Removing trash ${it.id} because of range"
		mapState.removeEntity(it)
		trash.remove(it)
	}
	// Despawn all those that are expired for time
	float t = Util.getGameTime()
	deadTrash = trash.findAll { it.getVar("despawnAt") < t }
	deadTrash.each {
		println "Removing trash ${it.id} because of despawn time"
		mapState.removeEntity(it)
		trash.remove(it)
	}
}

void timer(ent) {
	mapState = Util.getActiveMapState()
	playerPawn = mapState.getPlayerPawn()
	if(!playerPawn) return
	// Get all trash mobs
	def trash = mapState.getAllEntities().findAll { it instanceof MobilePawn && it.getVar("trash") }
	println "Existing trash: ${trash}"
	// Cleanup dead trash
	cleanupTrash(trash)
	// Spawn more trash if needed
	spawnMoreTrash(trash)
}
