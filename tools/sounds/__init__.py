"""Sound recipes, one module per family (fission, steam, backend, storage, grid, radiation).

A module exposes ``SOUNDS = [(event, recipe, meta), ...]``; ``recipe(k, rng)`` returns mono samples
in -1..1 built only from the synthesis kit ``k`` (tools/gen_sounds.py): oscillators, noise, filters
and envelopes. No samples, no recordings, no third-party audio. See gen_sounds.py for the contract.
"""
