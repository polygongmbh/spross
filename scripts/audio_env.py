"""Runs an audio script under the repo's own `.venv` (`requirements-audio.txt`), whatever
`python3` is today — Homebrew moves it a minor version ahead of the packages the measures
need. Imported before numpy; a script started any other way re-executes itself there."""
import os
import sys

VENV = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.venv')
PYTHON = os.path.join(VENV, 'bin', 'python')

if (os.path.exists(PYTHON) and os.path.realpath(sys.prefix) != os.path.realpath(VENV)
        and sys.argv[0].endswith('.py') and os.path.isfile(sys.argv[0])):
    os.execv(PYTHON, [PYTHON] + sys.argv)
