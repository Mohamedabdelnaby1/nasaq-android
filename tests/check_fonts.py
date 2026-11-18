from pathlib import Path
import struct
root=Path(__file__).resolve().parents[1]
def u16(b,o): return struct.unpack_from('>H',b,o)[0]
def u32(b,o): return struct.unpack_from('>I',b,o)[0]
for weight,expected in [('regular',400),('medium',500),('bold',700)]:
    b=(root/'app/src/main/res/font'/f'tajawal_{weight}.ttf').read_bytes()
    assert b[:4]==b'\0\1\0\0'
    tables={}
    for i in range(u16(b,4)):
        at=12+16*i
        tables[b[at:at+4].decode('ascii')]=(u32(b,at+8),u32(b,at+12))
    off,length=tables['name']
    assert 'Tajawal'.encode('utf-16-be') in b[off:off+length]
    assert u16(b,tables['OS/2'][0]+4)==expected
    cmap=tables['cmap'][0]
    subtables=[cmap+u32(b,cmap+4+8*i+4) for i in range(u16(b,cmap+2))]
    selected=next(offset for offset in subtables if u16(b,offset)==4)
    count=u16(b,selected+6)//2
    end=selected+14
    start=end+count*2+2
    delta=start+count*2
    ranges=delta+count*2
    def has_char(code):
        for i in range(count):
            if u16(b,start+2*i)<=code<=u16(b,end+2*i):
                r=u16(b,ranges+2*i)
                glyph=(code+u16(b,delta+2*i))%65536 if r==0 else u16(b,ranges+2*i+r+2*(code-u16(b,start+2*i)))
                return glyph!=0
        return False
    assert all(has_char(ord(c)) for c in 'يوميالسكر0123456789Yawmi')
    print(f'PASS Tajawal {weight} family, weight, Arabic and Latin glyph coverage')
