"""zipalign 대체: 압축하지 않은 항목(resources.arsc 등)을 4바이트 경계에 맞춰 다시 쓴다."""
import sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
NO_COMPRESS = ("resources.arsc", ".png", ".so")
with zipfile.ZipFile(src) as zin, open(dst, "wb") as f:
    zout = zipfile.ZipFile(f, "w")
    for info in zin.infolist():
        data = zin.read(info.filename)
        ni = zipfile.ZipInfo(info.filename, date_time=(2026, 11, 1, 0, 0, 0))
        ni.external_attr = info.external_attr
        if info.filename.endswith(NO_COMPRESS):
            ni.compress_type = zipfile.ZIP_STORED
            # 로컬 헤더(30바이트) + 파일명 + extra 뒤 데이터 시작 위치를 4의 배수로
            offset = f.tell() + 30 + len(ni.filename.encode())
            pad = (4 - offset % 4) % 4
            ni.extra = b"\x00" * pad
        else:
            ni.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(ni, data)
    zout.close()
