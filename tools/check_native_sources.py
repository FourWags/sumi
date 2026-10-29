#!/usr/bin/env python3
"""Check CMake's exact production source list against upstream NativeFileList.mk."""
from pathlib import Path
import subprocess,re
root=Path(__file__).resolve().parents[1]
jni=root/'app/src/main/jni'
make=(jni/'NativeFileList.mk').read_text()+"\n.PHONY: print-sources\nprint-sources:\n\t@printf '%s\\n' $(LATIN_IME_JNI_SRC_FILES) $(addprefix src/,$(LATIN_IME_CORE_SRC_FILES))\n"
expected=subprocess.check_output(['make','--no-print-directory','-f','-','print-sources'],input=make,text=True,cwd=jni).splitlines()
actual=re.findall(r'^    "([^"\n]+)"$',(jni/'NativeSources.cmake').read_text(),re.M)
assert actual==expected and len(actual)==len(set(actual)), 'CMake source list differs from upstream production sources'
assert all((jni/name).is_file() for name in actual)
print(f'PASS: {len(actual)} native production sources match NativeFileList.mk exactly')
