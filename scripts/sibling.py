"""Load a hyphenated sibling script as a module.

`scripts/` names its tools with hyphens, which no `import` statement accepts, so the
ones that share code are loaded by path instead. Underscore-named helpers next door
(`audio_gates.py`, `audio_measure.py`) are plain imports and need nothing from here.
"""
import importlib.util
import os


def load(name, filename):
    """The module in `scripts/<filename>`, bound to `name`."""
    spec = importlib.util.spec_from_file_location(
        name, os.path.join(os.path.dirname(os.path.abspath(__file__)), filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
