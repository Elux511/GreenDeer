# Live2D / Cubism SDK

本项目的数字人驱动拟采用 **Live2D Cubism SDK for Web**（需遵守 Live2D 授权条款）。

## 集成步骤（概要）

1. 从 Live2D 官方渠道获取 Cubism SDK for Web，将运行时放入例如 `public/cubism/` 或经构建链路拷贝。
2. 在 Vue 组件中挂载 canvas，按官方示例加载 `.model3.json` 与纹理。
3. 将 TTS 播放进度或音素事件对接到 SDK 的口型/参数驱动接口。

## 注意

- SDK 文件通常**不宜**直接提交到公开仓库；可使用 `.gitignore` 忽略并在 README 中说明获取方式。
- 若仅浏览器预览数字人，可与 OBS「浏览器源」配合输出到虚拟摄像头。
