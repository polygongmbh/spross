"""How `pesq.onnx` was made: torchaudio's SQUIM objective model, its PESQ branch alone, exported to ONNX
with its weights stored as float16 and cast back to float32 at load (half the size, within 0.003 of torch).

Needs torch, torchaudio and onnx, which the repo's .venv does not hold — run it from a scratch venv:
  python export.py pesq.onnx
"""
import os
import sys

import numpy as np
import onnx
import torch
from onnx import TensorProto, helper, numpy_helper
from torchaudio.pipelines import SQUIM_OBJECTIVE


class Layer(torch.nn.Module):
    """TransformerEncoderLayer (post-norm, relu, batch_first) written out: the traced original bakes
    the trace's length into its attention reshapes, so any other length fails."""

    def __init__(self, layer):
        super().__init__()
        self.layer, self.heads = layer, layer.self_attn.num_heads

    def forward(self, x):
        layer, attention = self.layer, self.layer.self_attn
        q, k, v = torch.nn.functional.linear(x, attention.in_proj_weight, attention.in_proj_bias).chunk(3, dim=-1)
        width = q.shape[-1] // self.heads

        def split(t):
            return t.reshape(t.shape[0], -1, self.heads, width).transpose(1, 2)

        weights = torch.softmax(split(q) @ split(k).transpose(-1, -2) / width ** 0.5, dim=-1)
        y = (weights @ split(v)).transpose(1, 2).reshape(x.shape[0], -1, self.heads * width)
        x = layer.norm1(x + attention.out_proj(y))
        return layer.norm2(x + layer.linear2(torch.relu(layer.linear1(x))))


class Pesq(torch.nn.Module):
    """SquimObjective's forward with the PESQ branch (the second) alone."""

    def __init__(self, model):
        super().__init__()
        branch = model.branches[1]
        self.encoder, self.dprnn = model.encoder, model.dprnn
        self.branch = torch.nn.Sequential(Layer(branch[0]), branch[1], branch[2])

    def forward(self, x):
        x = x / (torch.mean(x ** 2, dim=1, keepdim=True) ** 0.5 * 20)
        return self.branch(self.dprnn(self.encoder(x))).squeeze(dim=1)


def halve(model):
    """Every large float32 weight stored as float16 behind a Cast back to float32."""
    graph, kept, casts = model.graph, [], []
    for tensor in graph.initializer:
        array = numpy_helper.to_array(tensor)
        if array.dtype == np.float32 and array.size > 1024:
            half = numpy_helper.from_array(array.astype(np.float16), tensor.name + '_fp16')
            kept.append(half)
            casts.append(helper.make_node('Cast', [half.name], [tensor.name], to=TensorProto.FLOAT))
        else:
            kept.append(tensor)
    del graph.initializer[:]
    graph.initializer.extend(kept)
    nodes = list(graph.node)
    del graph.node[:]
    graph.node.extend(casts + nodes)
    onnx.checker.check_model(model)
    return model


out = sys.argv[1]
torch.onnx.export(Pesq(SQUIM_OBJECTIVE.get_model().eval()).eval(), (torch.randn(1, 32000),), out + '.fp32', dynamo=False, input_names=['audio'],
                  output_names=['pesq'], dynamic_axes={'audio': {1: 'time'}}, opset_version=17)
onnx.save(halve(onnx.load(out + '.fp32')), out)
os.remove(out + '.fp32')
