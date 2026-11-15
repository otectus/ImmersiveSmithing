"""Editable, deliberately placed pixel art. One texel per model unit; no noise or resampling.

PNG files are delivery exports. Change this source, then run tools/generate_textures.py --write.
Model JSON in assets/immersive_smithing/models is directly editable in Blockbench (Java Block/Item).
"""
from PIL import Image, ImageDraw

IRON = ['#171c22', '#2a3037', '#3d4852', '#596773', '#87949b', '#b6bfb9']
WOOD = ['#292321', '#42332b', '#604735', '#806044', '#a08055', '#bea171']
BRONZE = ['#49352c', '#755039', '#a77d4f', '#d2ad6b']
HEAT = ['#562a28', '#913c2a', '#cf5c2b', '#f39136', '#ffd36b', '#fff0b4']
TIERS = {
    'stone': ['#272831', '#43454e', '#666d72', '#929990', '#c0c4ac'],
    'iron': ['#20282f', '#4a5861', '#7c909a', '#bdc9c7', '#f0eee0'],
    'diamond': ['#123c46', '#1b666e', '#339e9e', '#6bdbcc', '#c2f7e0'],
    'netherite': ['#231e28', '#3e333e', '#605260', '#88757e', '#b3a0a0'],
}

def canvas(size, color=(0,0,0,0)):
    im = Image.new('RGBA', size, color)
    return im, ImageDraw.Draw(im)

def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im, d = canvas((16,16))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != '.': d.point((x,y), fill=palette[key])
    return im

HAMMER = [
'................',
'......000.......',
'.....03440......',
'....0332240.....',
'....03222240....',
'.....02122240...',
'......01122240..',
'......bb012230..',
'.....bHh001230..',
'....bHho..010...',
'...oHho....0....',
'..oHho..........',
'.oHho...........',
'.ohho...........',
'..oo............',
'................']
TONGS = [
'................',
'...........000..',
'..........0340..',
'.........0320...',
'........0320.00.',
'.......0320.0340',
'.......020.0320.',
'......00b00320..',
'.....oHob2200...',
'....oHho000.....',
'...oHho.0ho.....',
'..oHho.0hho.....',
'.oHho.0hho......',
'.oho.0hho.......',
'..o..0ho........',
'......0.........']
WORKPIECE = [
'................',
'..........oo....',
'.........o54o...',
'........o5541o..',
'.......o55431o..',
'......o554310...',
'.....o554310....',
'....o554310.....',
'...o554310......',
'..o554310.......',
'..o44310........',
'...o310.........',
'....00..........',
'................',
'................',
'................']
LOADED = [
'................',
'................',
'............oo..',
'...........o54o.',
'..........o5431o',
'..........o431o.',
'...........o1o..',
'............o...',
'................',
'................',
'................',
'................',
'................',
'................',
'................',
'................']
BOOK = [
'................',
'...oooooooooo...',
'..ohhHHHHHHHbo..',
'..ohmhhhhhhHppo.',
'..ohmhhhhhbhpqo.',
'..ohmhbbbbhhpqo.',
'..ohmhbBBbBhpqo.',
'..ohmhhBBbhhpqo.',
'..ohmhhBbBhhpqo.',
'..ohmhBBBBhhpqo.',
'..ohmhhhhhhhpqo.',
'..ohmhbbbbhhppo.',
'..ohhHHHHHHHbo..',
'..ommppppppppo..',
'...oooooooooo...',
'................']

def worked_timber():
    """Four oak staves, with interrupted grain instead of black ladder-like grooves."""
    return sprite([
        '3432343234433432', '4542344235433442', '4532344235433452', '3532343234433452',
        '3432343234433442', '3442344234533442', '3442345234533442', '3432345234433432',
        '3432344234433432', '4532344235433442', '4532343235433442', '4432343234433452',
        '3432344234433452', '3442344234533442', '3442343234533442', '3432343234433432',
    ], dict(zip('012345', ['#382c20','#4c3827','#63492f','#7b5b38','#8b693f','#9d7a4a'])))


def masonry(rim=False):
    """Irregular stone-brick courses; narrow mortar, chipped corners and connected clusters."""
    ramp = ['#393c38','#4b4e47','#5c6057','#6d7065','#7e8073','#8f9080','#a1a18e']
    if rim:
        ramp = ['#464940','#595d51','#6c7060','#7c806d','#8e927b','#9fa28b','#aeb197']
    return sprite([
        '0345542044544432', '3454432044333321', '2333421033322210', '1222100122211000',
        '0000345543203454', '4432454443214544', '3321333332103332', '2210122210001221',
        '0000000034544320', '4554432045443321', '3443321033332210', '2332210012221000',
        '1100345543203454', '0002454443214544', '4432333332103332', '3321122210001221',
    ], dict(zip('0123456', ramp)))


def forged_metal(top=False):
    """Cool iron with broad worked patches and short directional hammer scuffs."""
    ramp = ['#353c40','#454e52','#535e62','#646f73','#748085','#899498']
    if top:
        ramp = ['#4c5559','#606c70','#727f83','#859296','#97a3a5','#aeb8b6']
    return sprite([
        '3334444333344433', '4443333334333334', '3322223333222223', '3222333222223333',
        '2223433222332222', '2233322222344322', '2222223322333222', '2333223322222233',
        '2344322222233433', '2233222233322232', '2222222344322222', '2233322233222332',
        '2344322222223432', '2233222333222222', '2222222344322222', '1222221112222221',
    ], dict(zip('012345', ramp)))

def textures():
    out = {}
    for tier, ramp in TIERS.items():
        palette = {str(i):v for i,v in enumerate(ramp)} | {'o':IRON[0], 'b':BRONZE[2], 'H':WOOD[4], 'h':WOOD[2]}
        hammer = sprite(HAMMER, palette)
        d = ImageDraw.Draw(hammer)
        # Material-specific wear and facets; the metal stays dominant, as on vanilla tools.
        if tier == 'stone':
            d.point((7,2), fill=ramp[1]); d.point((12,8), fill=ramp[1]); d.point((5,4), fill=ramp[0])
        if tier == 'diamond':
            d.line((7,4,10,7), fill=ramp[3]); d.point((7,4), fill=ramp[4]); d.point((11,8), fill=ramp[1])
        if tier == 'netherite':
            d.line((7,4,10,7), fill=ramp[3]); d.line((8,6,11,9),fill=ramp[1]); d.point((6,7), fill=BRONZE[1])
        out[f'item/{tier}_smithing_hammer.png'] = hammer
        tongs = sprite(TONGS, palette)
        if tier == 'netherite':
            d = ImageDraw.Draw(tongs); d.point((11,3), fill=BRONZE[2]); d.point((13,6), fill=BRONZE[3])
        out[f'item/{tier}_smithing_tongs.png'] = tongs
    hot_palette = dict(zip('012345',HEAT)) | {'o': HEAT[0]}
    out['item/hot_workpiece.png'] = sprite(WORKPIECE, hot_palette)
    out['item/tongs_workpiece.png'] = sprite(LOADED, hot_palette)
    book = sprite(BOOK, {'o':IRON[0], 'h':WOOD[2], 'H':WOOD[3], 'm':WOOD[1], 'b':BRONZE[2], 'B':BRONZE[3], 'p':'#dbc9a0', 'q':'#af946c'})
    d=ImageDraw.Draw(book)
    d.rectangle((6,5,10,10),fill=WOOD[2])
    d.rectangle((6,6,10,7),fill=BRONZE[2]); d.line((6,6,9,6),fill=BRONZE[3])
    d.line((8,8,8,10),fill=BRONZE[3]); d.point((9,10),fill=BRONZE[1])
    out['item/smithing_guide.png'] = book

    out['block/forge_masonry.png']=masonry()
    out['block/forge_rim.png']=masonry(True)
    im=forged_metal(); d=ImageDraw.Draw(im)
    d.line((0,0,15,0),fill=IRON[4]); d.line((0,15,15,15),fill=IRON[1])
    for x,y in [(2,2),(13,2),(2,13),(13,13)]:
        d.point((x,y), fill=IRON[4]); d.point((x,y+1),fill=IRON[1])
    out['block/iron_plate.png']=im
    im=sprite([
        '1222100122210111','2343211234321221','2333212333322342','1222101222212332',
        '0111010011101221','1222211222100110','2343322343211221','2333222333223432',
        '1222111222123332','0110010011012221','1221122212101100','2342234333211221',
        '2332233332223432','1221122221123332','0110011100112221','1221100122210110',
    ],dict(zip('01234',['#17191a','#202222','#292c2b','#333633','#41443d'])))
    out['block/forge_inner.png']=im
    im=out['block/forge_inner.png'].copy(); d=ImageDraw.Draw(im)
    # Small ember seams between charcoal chunks, rather than a bright tiled diagonal lattice.
    for pts in [[(0,4),(2,4),(3,5)],[(7,0),(7,2)],[(10,7),(12,7)],
                [(4,10),(5,11),(7,11)],[(12,13),(13,14),(15,14)],[(0,14),(1,15)]]:
        d.line(pts,fill=HEAT[2])
    for x,y in [(1,4),(7,1),(11,7),(5,11),(14,14)]: d.point((x,y),fill=HEAT[4])
    out['block/forge_coals.png']=im
    im=forged_metal(True);d=ImageDraw.Draw(im)
    # The working face samples x 4..12, z 4..15. Put the bevel on those edges, not outside its UVs.
    d.line((4,4,10,4),fill='#b5bfbc');d.line((4,5,4,13),fill='#9da9a9')
    d.line((11,4,11,14),fill='#59676b');d.line((5,14,10,14),fill='#647175')
    # Keep the working face uninterrupted; tiny scuffs already come from the forged-metal source.
    out['block/smiths_anvil_top.png']=im
    im=forged_metal();d=ImageDraw.Draw(im)
    d.line((0,0,15,0),fill='#8c989b');d.line((0,2,15,2),fill='#454f54')
    out['block/smiths_anvil_body.png']=im
    out['block/worked_timber.png']=worked_timber()
    # Station details that used to be separate cuboids are painted on the faces that carried them (1.2 geometry pass).
    # Anvil stump: two forged bands at the heights of the old band rings (y 2-3 and 7-8, texture rows 13 and 8).
    im=worked_timber(); d=ImageDraw.Draw(im)
    for row in [8,13]:
        d.line((0,row,15,row),fill=IRON[3]); d.line((0,row+1,15,row+1),fill=IRON[1])
        for x in [2,8,13]: d.point((x,row),fill=BRONZE[2])
        d.point((8,row),fill=BRONZE[3])
    out['block/anvil_stump.png']=im
    # Trough staves: two iron straps down each face (where the old strap cuboids stood, x 3-4 and 12-13) with rivets.
    im=worked_timber(); d=ImageDraw.Draw(im)
    for col in [3,12]:
        d.line((col,0,col,15),fill=IRON[3]); d.line((col+1,0,col+1,15),fill=IRON[1])
        for y in [7,11,14]: d.point((col,y),fill=BRONZE[2])
    d.line((0,7,15,7),fill=WOOD[1])
    out['block/trough_staves.png']=im
    im=sprite([
        '3333344333333333','3444454444444433','3453333333333543','3433444444433543',
        '3434533333543543','3434534443543543','3434534333543543','3434534343543543',
        '3434533443543543','3434533333543543','3433444444433543','3433333333333543',
        '3444443444444543','3333332333333443','3444432344444433','3333323333333333',
    ],dict(zip('2345',['#63492f','#82623b','#987544','#ab8750'])))
    out['block/timber_end.png']=im
    im,d=canvas((16,16),'#85877d')
    # Wheel side UVs cover [3,3,13,13], centred on the axle at [8,8].
    for a,c in [(3,'#636b64'),(4,'#a6a799'),(6,'#747d74')]:d.ellipse((a,a,15-a,15-a),outline=c)
    for x,y in [(5,5),(10,4),(11,7),(9,11),(4,8),(6,10)]:
        d.point((x,y),fill='#b1b1a1');d.point((x,y+1),fill='#777e73')
    d.rectangle((7,7,8,8),fill=IRON[2]);d.point((7,7),fill=IRON[4])
    out['block/grinding_wheel_side.png']=im
    # Restrained grit. No bright hoops across the edge of the turning stone.
    im=sprite([
        '3334433332333433','3443332333334332','3332333343333323','2333344333233344',
        '3334333333344333','3443323334333323','3332333443332333','2333433332333443',
        '3333332333443333','3443333433332333','3332333332333344','2333344333344333',
        '3334333323333332','3443332333443323','3332333443332333','2333433332333443',
    ],dict(zip('234',['#74796e','#878c7e','#9a9e8d'])))
    out['block/grinding_wheel_edge.png']=im
    # Billet: a light neutral base. Renderers multiply it by the cold metal colour or the heat colour, so it must be pale
    # for hot metal to glow orange rather than brown.
    im=sprite([
        '3333333344333333','3344333333332233','3333322333333333','3333333333443333',
        '3322333333333333','3333333443332233','3334433333333333','3333333332233333',
        '3333333333333443','3443333322333333','3333333333333333','3333223333443333',
        '3333333343333333','3322333333332233','3333333334433333','3333344333333333',
    ],dict(zip('234',['#bfc1c1','#d7d8d6','#eaebe6'])))
    out['block/billet.png']=im
    # Molten metal: connected oxide islands and narrow hot channels, animated in place in four discrete steps.
    molten = [
        '3334433332233333','3345433321123333','3444333221233443','3333322212334543',
        '3223333223334433','2112333333333333','2123334433332233','3233345433321123',
        '3333444333321233','3333333333332333','3344333223333344','3454332112333443',
        '3443332123334433','3333333233333333','3322333334433333','3212333345433333',
    ]
    im,d=canvas((16,64))
    for f in range(4):
        tile=sprite(molten, dict(zip('012345',HEAT)))
        td=ImageDraw.Draw(tile)
        for x,y in [(3,2),(6,7),(12,11)]:
            td.point((x,y),fill=HEAT[4 if f in (0,3) else 3])
            td.point((x+1,y),fill=HEAT[3 if f in (0,3) else 4])
        im.paste(tile,(0,f*16))
    out['block/molten_metal.png']=im
    for frame in range(4):
        im,d=canvas((8,8))
        for x,y in [(3,3),(4,3),(3,4),(4,4)][:max(1,4-frame)]:d.point((x,y),fill='white')
        out[f'particle/spark_{frame}.png']=im
    # Minigame target sprites only: ring (96,0,64,64) and disc (160,0,64,64), a stable resource-pack contract.
    # The screens themselves are drawn from Minecraft's own GUI textures; there is no themed frame any more.
    im,d=canvas((256,256))
    # Hard edged targets; alpha only at the outermost circle boundary is unnecessary at pixel GUI scale.
    for x,y in [(96,0),(160,0)]:
        d.ellipse((x+2,y+2,x+61,y+61),fill='white')
    d.ellipse((102,6,153,57),fill=(0,0,0,0))
    out['gui/smithing.png']=im
    return out
