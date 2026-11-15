"""Generate unchanged upstream speech codebooks with the host compiler, offline."""
from pathlib import Path
import tarfile, subprocess, hashlib
root = Path(__file__).resolve().parent
work = root / 'build/native'
srcroot = work / 'source'
gen = work / 'generated'
srcroot.mkdir(parents=True, exist_ok=True)
(gen/'codec2').mkdir(parents=True, exist_ok=True)
archive = root/'upstream/codec2-1.2.0.tar.gz'
if hashlib.sha256(archive.read_bytes()).hexdigest() != 'a78184eb3510de152ee225a0716d9a72f8f4bdbe79031783c5bbd84a9787d846':
    raise ValueError('Codec2 source checksum mismatch')
with tarfile.open(archive) as t:
    for member in t.getmembers():
        dest = (srcroot/member.name).resolve()
        if not dest.is_relative_to(srcroot.resolve()) or member.issym() or member.islnk():
            raise ValueError('Unsafe source archive member')
    t.extractall(srcroot)
src = srcroot/'src'
version = (srcroot/'cmake/version.h.in').read_text()
for key,value in {'CODEC2_VERSION_MAJOR':'1','CODEC2_VERSION_MINOR':'2','CODEC2_VERSION_PATCH':'0','CODEC2_VERSION':'1.2.0'}.items():
    version = version.replace('@'+key+'@',value)
(gen/'codec2/version.h').write_text(version.replace('#cmakedefine','#define'))
subprocess.run(['cc','-O2',str(src/'generate_codebook.c'),'-o',str(work/'generate_codebook')],check=True)
tables = [('codebook','lsp_cb',[f'lsp{i}.txt' for i in range(1,11)]),
 ('codebookd','lsp_cbd',[f'dlsp{i}.txt' for i in range(1,11)]),
 ('codebookjmv','lsp_cbjmv',[f'lspjmv{i}.txt' for i in range(1,4)]),
 ('codebookge','ge_cb',['gecb.txt']),
 ('codebooknewamp1','newamp1vq_cb',['train_120_1.txt','train_120_2.txt']),
 ('codebooknewamp1_energy','newamp1_energy_cb',['newamp1_energy_q.txt']),
 ('codebooknewamp2','newamp2vq_cb',['codes_450.txt']),
 ('codebooknewamp2_energy','newamp2_energy_cb',['newamp2_energy_q.txt'])]
for name,symbol,files in tables:
    data=subprocess.check_output([str(work/'generate_codebook'),symbol,*[str(src/'codebook'/f) for f in files]])
    (gen/(name+'.c')).write_bytes(data)
