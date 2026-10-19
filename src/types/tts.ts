/** TTS 接口类型（后端 TtsController）。 */

/** GET /api/v1/tts/voices 元素 —— TtsSynthesizer.VoiceInfo */
export interface VoiceInfo {
  name: string
  provider: string
  sampleRate: number
  description: string
  available: boolean
}

/** POST /api/v1/tts/synthesize 响应 */
export interface SynthesizeResp {
  provider: string
  mime: string
  sampleRate: number
  durationMs: number
  audioBase64: string
}
