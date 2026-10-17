"""Reviewed policy for tools/generate_epic_compat.py: which jars, how metals are named, which anvil pattern each weapon
type uses, and every decision that cannot be read from a recipe (unique relics, assemblies, visor states, the new
Epic Fight recipes). The engine applies these rules to the pinned jars; anything it cannot decide is an error, never a
guess. Change a rule here, then run `python3 tools/generate_epic_compat.py --write` and review the diff.
"""

# --------------------------------------------------------------------------------------------------- pinned inputs
# mod id -> (integration, jar source). Sources: ('modrinth', slug, gradle property) or ('curse', gradle property).
MODS = {
    'epicfight': ('epicfight', ('modrinth', 'epic-fight', 'epic_fight_modrinth_id')),
    'magistuarmory': ('epicknights', ('modrinth', 'epic-knights-shields-armor-and-weapons', 'epic_knights_modrinth_id')),
    'magistuarmoryaddon': ('epicknights', ('modrinth', 'epic-knights-addon', 'ek_addon_modrinth_id')),
    'antiquelegacy': ('epicknights', ('modrinth', 'epic-knights-antique-legacy', 'ek_antique_legacy_modrinth_id')),
    'slavicarmory': ('epicknights', ('modrinth', 'epic-knights-slavic-armory', 'ek_slavic_armory_modrinth_id')),
    'ekotsk': ('epicknights', ('modrinth', 'epic-knights-of-the-seven-kingdoms', 'ek_seven_kingdoms_modrinth_id')),
    'epic_knights_ice_and_fire': ('epicknights', ('curse', 'ek_ice_and_fire_curse')),
    'epic_knights_ores_and_alloys': ('epicknights', ('curse', 'ek_ores_and_alloys_curse')),
    'darkagesarmory': ('epicknights', ('curse', 'ek_dark_ages_curse')),
    'epicknights_open_helms': ('epicknights', ('curse', 'ek_open_helms_curse')),
    'artillery_addon': ('epicknights', ('curse', 'ek_artillery_curse')),
    'medieval_boomsticks': ('epicknights', ('curse', 'ek_boomsticks_curse')),
}
# Jars read for recipes and tags only (ingredients of the mods above): their own items are never generated.
SUPPORT_JARS = {
    'quick_swap': ('curse', 'ek_swappable_helms_curse'),
    'iceandfire': ('modrinth', 'ice-and-fire-dragons', 'ice_and_fire_modrinth_id'),
}
# Built-in data that only matches one version of a mod (the same recipe ids with other ingredients) carries a
# immersive_smithing:mod_version condition with this range.
VERSION_RANGES = {
    'medieval_boomsticks': '[1.2,1.3)',
}

# --------------------------------------------------------------------------------------------------- metals
# forge:<form>/<name> tags -> material family. Forms and their units.
METALS = {
    'iron': 'minecraft:iron', 'gold': 'minecraft:gold', 'copper': 'minecraft:copper', 'netherite': 'minecraft:netherite',
    'steel': 'forge:steel', 'silver': 'forge:silver', 'tin': 'forge:tin', 'bronze': 'forge:bronze',
    'fire_dragonsteel': 'forge:fire_dragonsteel', 'ice_dragonsteel': 'forge:ice_dragonsteel',
    'lightning_dragonsteel': 'forge:lightning_dragonsteel',
    'blacked_steel': 'forge:blacked_steel', 'gilded_steel': 'forge:gilded_steel',
}
FORMS = {'ingots': 9, 'nuggets': 1, 'storage_blocks': 81}
# Gems that make the diamond shape a netherite upgrade is priced from (9 units per gem slot, like an ingot).
GEMS = {'forge:gems/diamond', 'minecraft:diamond'}
NETHERITE_TEMPLATE = 'minecraft:netherite_upgrade_smithing_template'
NETHERITE_FAMILY = 'minecraft:netherite'
# Families that need another mod's ingots: their recipes stay inactive (and are reported) while the family is absent.
OPTIONAL_FAMILIES = {'forge:silver', 'forge:tin', 'forge:bronze'}

# Plate items proven at one ingot each by their own recipe (two ingots make two plates): melt sources of their family.
# Added as sources_only material files, so a stray plate melts for exactly what it cost.
PLATE_SOURCES = {
    'magistuarmory:steel_plate': 'forge:steel',
    'antiquelegacy:iron_plate': 'minecraft:iron',
    'antiquelegacy:bronze_plate': 'forge:bronze',
    'ekotsk:blacked_steel_plate': 'forge:blacked_steel',
    'ekotsk:gilded_steel_plate': 'forge:gilded_steel',
}
# New metal families of the Seven Kingdoms (blackened and gilded steel): full definitions with names and tints.
NEW_FAMILIES = {
    'forge:blacked_steel': {'mod': 'ekotsk', 'display_name': 'material.forge.blacked_steel', 'tint': '#7A6E78',
                            'melt_time_multiplier': 1.3},
    'forge:gilded_steel': {'mod': 'ekotsk', 'display_name': 'material.forge.gilded_steel', 'tint': '#FFC468',
                           'melt_time_multiplier': 1.3},
}

# --------------------------------------------------------------------------------------------------- patterns
# Anvil pattern by the last word(s) of the item path. Longest match wins; armour uses its slot, shields use 'shield'.
PATTERNS = {
    # Epic Fight
    'dagger': 'dagger', 'longsword': 'longsword', 'greatsword': 'greatsword', 'spear': 'spear', 'tachi': 'katana',
    'uchigatana': 'katana', 'glove': 'cestus',
    # Epic Knights
    'stylet': 'dagger', 'shortsword': 'sword', 'katzbalger': 'sword', 'bastardsword': 'longsword', 'claymore': 'greatsword',
    'zweihander': 'greatsword', 'flamebladedsword': 'greatsword', 'estoc': 'rapier', 'pike': 'pike', 'ranseur': 'pike',
    'ahlspiess': 'pike', 'chivalrylance': 'lance', 'lochaberaxe': 'halberd', 'concavehalberd': 'halberd',
    'guisarme': 'glaive', 'heavymace': 'flanged_mace', 'morgenstern': 'flanged_mace', 'chainmorgenstern': 'flail',
    'heavywarhammer': 'battle_hammer', 'lucernhammer': 'warhammer', 'barbedclub': 'club', 'blacksmith_hammer': 'warhammer',
    'pitchfork': 'spear', 'messer_sword': 'saber', 'heavy_crossbow': 'heavy_crossbow',
    # Epic Knights: Addon, Dark Ages, Slavic Armory, Antique Legacy
    'sword': 'sword', 'arming_sword_type_xiii': 'sword', 'arming_sword_type_xiv': 'sword', 'arming_sword_type_xv': 'sword',
    'bar_mace': 'flanged_mace', 'round_mace': 'flanged_mace', 'battleaxe': 'battleaxe', 'broadaxe': 'battleaxe',
    'small_broadaxe': 'axe', 'daneaxe': 'battleaxe', 'gallowglass_axe': 'battleaxe', 'war_axe': 'axe', 'axe': 'axe',
    'francisca_axe': 'tomahawk', 'billhook': 'glaive', 'fauchard': 'glaive', 'voulge': 'glaive', 'welsh_guisarme': 'glaive',
    'glaive': 'glaive', 'boar_spear': 'spear', 'short_spear': 'spear', 'hammer_spear': 'spear', 'military_fork': 'spear',
    'partisan': 'spear', 'goedendag': 'spear', 'rogatina': 'spear', 'winged_rogatina': 'spear', 'decorated_spear': 'spear',
    'harnham_spear': 'spear', 'kingston_spear': 'spear', 'antique_spear': 'spear', 'doru': 'spear', 'triarii_spear': 'spear',
    'sarissa': 'pike', 'retiarius': 'spear', 'javelin': 'javelin', 'bollock_dagger': 'dagger', 'rondel_dagger': 'dagger',
    'parrying_dagger': 'parrying_dagger', 'swordbreaker': 'parrying_dagger', 'antique_dagger': 'dagger', 'sica': 'dagger',
    'short_seax': 'dagger', 'small_seax': 'dagger', 'scramasax': 'sword', 'sittingbourne_seax': 'dagger',
    'long_seax': 'sword', 'langseax': 'sword', 'cavalry_sabre': 'saber', 'sabre': 'saber', 'magyar_sabre': 'saber',
    'scimitar': 'saber', 'falchion': 'saber', 'cutlass': 'saber', 'maciejowski_messer': 'saber', 'kopis': 'saber',
    'grand_falchion': 'greatsword', 'executioners_sword': 'greatsword', 'german_greatsword': 'greatsword',
    'two_handed_messer': 'greatsword', 'rhomphaia': 'greatsword', 'feder': 'longsword', 'king_sword': 'longsword',
    'sidesword': 'rapier', 'rapier': 'rapier', 'lance': 'lance', 'english_poleaxe': 'halberd', 'italian_poleaxe': 'halberd',
    'french_halberd': 'halberd', 'swiss_halberd': 'halberd', 'bardiche': 'halberd', 'crow_beak': 'warhammer',
    'war_hammer': 'warhammer', 'two_handed_evening_star': 'battle_hammer', 'scythe': 'scythe', 'sickle': 'scythe',
    'gladius': 'sword', 'republic_gladius': 'sword', 'xiphos': 'sword', 'spatha': 'longsword', 'early_spatha': 'longsword',
    'single_edged_sword': 'sword', 'gladiator_sword': 'sword', 'celtic_sword': 'longsword', 'antique_sword': 'sword',
    # Ores and Alloys tools
    'pickaxe': 'pickaxe', 'shovel': 'shovel', 'hoe': 'hoe',
    # Artilleries and Firearms, Medieval Boomsticks
    'handgonne': 'heavy_crossbow', 'hurlbat': 'throwing_knife', 'throwing_cross': 'throwing_knife', 'francisca': 'tomahawk',
    'throwing_axe': 'tomahawk', 'throwing_knife': 'throwing_knife', 'eveningstar': 'flail', 'morningstar': 'flanged_mace',
    'arbalest': 'heavy_crossbow', 'war_dart': 'javelin', 'hand_cannon': 'heavy_crossbow', 'mini_pistola': 'heavy_crossbow',
    'eagle_standard': 'spear',
}

# --------------------------------------------------------------------------------------------------- recipe policy
# Native recipes that remake a finished piece without adding metal stay next to the forge recipe (a forged sword
# keeps its quality when set on a shaft): the forge recipe is only derived from them.
KEEP_NATIVE = {
    'epicfight:iron_spear', 'epicfight:golden_spear',
}
# Recipes the generator must ignore as manufacturing routes (alternatives priced elsewhere, or not a route at all).
IGNORE_RECIPES = {
    # A wooden shield made with a bronze boss instead of steel: the base mod's steel recipe prices metal shields.
    'antiquelegacy:wood_ellipticalshield_from_bronze', 'antiquelegacy:wood_roundshield_from_bronze',
}
# Primary material where the repair material does not say (no repair item, or a piece of two metals in equal parts).
PRIMARY = {
    'magistuarmory:heavy_crossbow': 'forge:steel',
}
# Equipment the forge makes although the recipe is mostly non-metal components (decided by hand, with the reason).
FORCE_FORGE = {
    'epicfight:glove': 'iron-studded leather glove: the forge works its two iron nuggets, the leather is a component',
}

# New recipes for items whose mod ships none (never a guess: each one is a reviewed balance decision).
SPECIAL_FORGE = {
    # Epic Fight's uchigatana is chest loot only. A diamond-stage katana: an iron blade (a tachi's 36 units) with a
    # diamond-hardened edge, leather grip wrap and a stick tang. Melts back for its iron only.
    'epicfight:uchigatana': {
        'material': 'minecraft:iron', 'units': 36, 'pattern': 'katana',
        'auxiliary': [({'item': 'minecraft:diamond'}, 1), ({'item': 'minecraft:leather'}, 2), ({'item': 'minecraft:stick'}, 1)],
    },
}
SPECIAL_CRAFTING = {
    # Epic Fight's wooden practice sword has no recipe: two planks on a stick, diagonally (no other recipe uses it).
    'epicfight:bokken': {
        'pattern': ['  P', ' P ', 'S  '], 'key': {'P': {'tag': 'minecraft:planks'}, 'S': {'item': 'minecraft:stick'}},
    },
}

# --------------------------------------------------------------------------------------------------- coverage
# Items without a forge recipe on purpose. (disposition, reason); items of every mod in MODS must be decided.
PROTECTED = {}


def _protect(ids, disposition, reason):
    for item in ids:
        PROTECTED[item] = (disposition, reason)


_protect(['ekotsk:' + n for n in ['aegon_dagger', 'blackfyre', 'brightroar', 'crab_pincer', 'dark_sister', 'dawn',
                                   'heartsbane', 'ice', 'lady_forlorn', 'lamentation', 'longclaw', 'nightfall', 'orphanmaker',
                                   'red_rain', 'robert_hammer', 'sandoq', 'truth', 'vigilance', 'valyrian_steel_helmet',
                                   'valyrian_steel_chestplate', 'valyrian_steel_leggings', 'valyrian_steel_boots',
                                   'aegon_crown_decoration']],
         'protected_loot', 'world-limited relic of the Seven Kingdoms (unique loot): never manufactured, never melted down')
_protect(['magistuarmory:' + n for n in ['rusted_bastardsword', 'rusted_heavymace', 'rustedbarbute', 'rustedchainmail_boots',
                                          'rustedchainmail_chestplate', 'rustedchainmail_helmet', 'rustedchainmail_leggings',
                                          'rustedcrusader_boots', 'rustedcrusader_chestplate', 'rustedgreathelm',
                                          'rustedhalfarmor_chestplate', 'rustedkettlehat', 'rustednorman_helmet']],
         'protected_loot', 'rusted loot from Epic Knights chests: never manufactured')
_protect(['magistuarmory:corruptedroundshield'], 'protected_loot', 'corrupted loot shield from Epic Knights chests')
_protect(['magistuarmory:crown_decoration', 'magistuarmory:minicrown_decoration'], 'protected_loot',
         'wearable crown found as loot or traded by Epic Knights villagers: never manufactured')
_protect(['magistuarmory:club'], 'protected_loot', 'wooden club carried by Epic Knights mobs; the mod ships no recipe')
_protect(['antiquelegacy:weathered_corinthian_helmet', 'antiquelegacy:weathered_eagle_standard'], 'protected_loot',
         'weathered antique found as loot; the eagle standard is restored with Antique Legacy\'s own gilding-template recipe')
_protect(['antiquelegacy:fur_cloak_decoration', 'antiquelegacy:golden_laurel_wreath_decoration',
          'antiquelegacy:laurel_wreath_decoration'], 'protected_loot', 'Antique Legacy loot decoration; the mod ships no recipe')
_protect(['magistuarmoryaddon:dark_gilded_parade_boots', 'magistuarmoryaddon:dark_gilded_parade_burgonet',
          'magistuarmoryaddon:dark_gilded_parade_chestplate', 'magistuarmoryaddon:silvered_dark_half_armor',
          'magistuarmoryaddon:dueling_shield'], 'protected_loot', 'Epic Knights: Addon loot piece; the mod ships no recipe')
_protect(['magistuarmoryaddon:darkblued_armet', 'magistuarmoryaddon:darkblued_sallet'], 'disabled_by_upstream',
         'its darkening recipe uses the removed minecraft:smithing type, so the add-on ships no working route')
_protect(['epicknights_open_helms:' + n for n in ['gilded_exquiste_maximilian_helmet', 'open_blue_armet_helmet',
                                                   'open_patrician_helmet', 'openonepiecesallet_helmet']],
         'disabled_by_upstream', 'Open Helms registers this visor state, but Swappable Helms\' default pairs do not include it '
         '(add the pair to quick_swap\'s HelmetPairs to reach it)')
_protect(['artillery_addon:' + n for n in ['chu_ko_nu', 'improved_gunsmithkit', 'noble_handgonne', 'tiller_gun',
                                            'windlass_crossbow']],
         'protected_loot', 'Artilleries and Firearms loot or trade item; the mod ships no recipe')
_protect(['epicfight:stray_hat', 'epicfight:stray_robe', 'epicfight:stray_pants'], 'non_equipment',
         'cloth armour worn by Epic Fight\'s stray mobs: not metal work, no recipe')
_protect(['medieval_boomsticks:smithing_hammer', 'medieval_boomsticks:smithing_tongs', 'medieval_boomsticks:smithing_bellows'],
         'non_equipment', 'tool of Medieval Boomsticks\' own smithing furnace: crafted as the mod ships it')

# Firearms and machines assembled from mechanisms (locks, triggers, gunsmith kits): the forge never makes these.
ASSEMBLY_PARTS = {'artillery_addon:s_trigger', 'artillery_addon:matchlock_mechanism', 'artillery_addon:wheel_lock',
                  'artillery_addon:gunsmithkit', 'artillery_addon:improved_gunsmithkit', 'artillery_addon:hair_trigger',
                  'artillery_addon:match', 'artillery_addon:pyrite', 'medieval_boomsticks:gun_trigger',
                  'artillery_addon:wheellock_designs'}
ASSEMBLY_REASON = 'firearm assembled from a lock or trigger mechanism and a gunsmith kit: gunsmithing stays with the mod'
# Medieval Boomsticks 1.2 has its own smithing furnace: alloy bars and barrels come only from it, so do the pieces made
# from them (and their upgrade kit's upgrades).
OWN_SMITHING_PARTS = {'medieval_boomsticks:' + n for n in ['dziwer_bar', 'bulat_bar', 'iron_barrel', 'small_iron_barrel',
                                                           'large_iron_barrel', 'gunmetal_barrel', 'small_gunmetal_barrel',
                                                           'large_gunmetal_barrel', 'upgrade_kit']}
OWN_SMITHING_REASON = 'needs alloy bars or barrels from Medieval Boomsticks\' own smithing furnace: that system makes it'
# Not equipment the forge works although damageable (ammunition flasks, ramrods, spanners, cooking pots).
NON_EQUIPMENT = {
    'artillery_addon:boneflask': 'powder flask (ammunition supply)', 'artillery_addon:horn_flask': 'powder flask (ammunition supply)',
    'artillery_addon:wooden_flasks': 'powder flask (ammunition supply)', 'artillery_addon:ramrod': 'loading tool for firearms',
    'artillery_addon:iron_ramrod': 'loading tool for firearms', 'artillery_addon:worm_ramrod': 'loading tool for firearms',
    'artillery_addon:wheellock_spanner': 'loading tool for wheellock firearms',
    'artillery_addon:iron_wheellock_spanner': 'loading tool for wheellock firearms',
    'artillery_addon:gunsmithkit': 'gunsmith\'s repair kit (a firearm component)',
    'artillery_addon:wheellock_designs': 'firearm blueprint item',
}
