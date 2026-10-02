# Condense `xcodebuild … 2>&1` into one line per target step, plus diagnostics.
#   xcodebuild … build 2>&1 | awk -f scripts/xcode-progress.awk
# Exits non-zero unless the build reported success, so the pipeline's status is the build's.

failed { print; next }
/^\*\* BUILD SUCCEEDED \*\*/ { ok = 1; next }
/^\*\* BUILD FAILED \*\*/ || /^The following build commands failed:/ { failed = 1; print; next }
/: (error|warning): / || /^e: / { if (!seen[$0]++) print; next }

/^PhaseScriptExecution / { step = $0; sub(/^PhaseScriptExecution /, "", step); sub(/ \/.*/, "", step); gsub(/\\ /, " ", step) }
/^(SwiftCompile|CompileSwift|SwiftEmitModule|CompileC) / { step = "compiling" }
/^CompileAssetCatalog/ { step = "assets" }
/^Ld / { step = "linking" }
/^CodeSign / { step = "signing" }

step != "" && match($0, /\(in target '[^']+'/) {
  target = substr($0, RSTART + 12, RLENGTH - 13)
  if (target ": " step != last) { last = target ": " step; print "  " last; fflush() }
}
{ step = "" }

END { exit !ok }
