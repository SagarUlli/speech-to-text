// Runs on the audio thread: downsamples mic audio to 16 kHz mono PCM16 and posts ~100 ms frames.
class PcmProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / 16000; // sampleRate is the AudioContext rate (usually 44.1k/48k)
    this.pos = 0;
    this.out = new Int16Array(1600);
    this.n = 0;
    this.acc = 0; this.cnt = 0; this.next = this.ratio;
    this.peak = 0;
  }
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (!ch) return true;
    for (let i = 0; i < ch.length; i++) {
      const v = ch[i];
      this.acc += v; this.cnt++;
      if (Math.abs(v) > this.peak) this.peak = Math.abs(v);
      this.pos++;
      if (this.pos >= this.next) {            // box-filter average = cheap anti-aliased downsample
        const s = Math.max(-1, Math.min(1, this.acc / this.cnt));
        this.out[this.n++] = s < 0 ? s * 0x8000 : s * 0x7fff;
        this.acc = 0; this.cnt = 0; this.next += this.ratio;
        if (this.n === this.out.length) {
          this.port.postMessage({ pcm: this.out.buffer, level: this.peak }, [this.out.buffer]);
          this.out = new Int16Array(1600); this.n = 0; this.peak = 0;
        }
      }
    }
    if (this.pos > 1e9) { this.pos = 0; this.next = this.ratio; }
    return true;
  }
}
registerProcessor('pcm-processor', PcmProcessor);
