## Rapid paraformer
- 模型出自阿里达摩院[Paraformer语音识别-中文-通用-16k-离线-large-长音频版](https://modelscope.cn/models/damo/speech_paraformer-large-vad-punc_asr_nat-zh-cn-16k-common-vocab8404-pytorch/summary)
- 本分支在安卓端实现音频录制并调用funasr/runtime/python/grpc接口，完成离线语音识别

## TODO
- 时间戳解析，基于时间戳支持用户文本编辑
- score解析，基于score支持用户文本编辑
- GIF展示识别全流程