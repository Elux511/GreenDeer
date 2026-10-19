import { http } from '@/api/client'
import type { SynthesizeResp, VoiceInfo } from '@/types/tts'

/** TTS REST（后端 TtsController，前缀 /api/v1/tts）。 */

export function listVoices(): Promise<VoiceInfo[]> {
  return http.get<VoiceInfo[]>('/tts/voices')
}

export function synthesize(text: string, voice?: string): Promise<SynthesizeResp> {
  return http.post<SynthesizeResp>('/tts/synthesize', voice ? { text, voice } : { text })
}

/** base64 + mime → 可直接喂给 <audio src> 的 data URL */
export function toAudioDataUrl(resp: SynthesizeResp): string {
  const mime = resp.mime || 'audio/wav'
  return `data:${mime};base64,${resp.audioBase64}`
}
