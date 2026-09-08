// 피사계 심도(depth of field — 초점 밖이 흐려지는 것). **index.html 이 기본으로 부른다.**
//
//   ?dof=off              끈다 (index.html 이 아예 이 파일을 안 부른다)
//   window.__dof.on=false 이미 켜진 것을 끈다 (레이어는 남고 아무 일도 안 한다)
//   window.__dof          값을 손으로 바꾼다 — focus·range·maxR·taps·zoomStart
//
// 아래 기본값(120 m 또렷 → 1,020 m 완전히 흐림, 확대 16.5 부터)은 화면을 눈으로 보고
// 고른 것이다. 계산으로 나온 값이 아니라 **고른 값**이라 여기 적어 둔다.
//
// 무엇을 재는가 — **피사계 심도(depth of field, 초점 밖이 흐려지는 것)를
// MapLibre 위에 후처리로 걸 수 있는가.** 걸 수 있으려면 두 가지가 필요하다:
//   ① 다 그린 화면의 **색**
//   ② 그 픽셀이 **얼마나 먼가**(깊이)
// MapLibre 는 둘 다 화면 버퍼(기본 프레임버퍼)에 남긴다. 커스텀 레이어를 맨 위에
// 얹으면 그 시점에 둘 다 완성되어 있고, WebGL2 의 blitFramebuffer 로 **깊이까지
// 통째로 복사**할 수 있다 — 이게 이 실험의 핵심 질문이었다.

const VS = `#version 300 es
void main(){
  vec2 p = vec2((gl_VertexID<<1)&2, gl_VertexID&2);
  gl_Position = vec4(p*2.0-1.0, 0.0, 1.0);
}`;

const FS = `#version 300 es
precision highp float;
uniform sampler2D uColor;
uniform highp sampler2D uDepth;
uniform vec2  uPix;      // 1 / 화면 크기
uniform float uDMax;     // painter.depthRangeFor3D[1]
uniform float uNear;     // Z 단위
uniform float uFar;      // Z 단위
uniform float uMPU;      // Z 단위 하나가 몇 m 인가
uniform float uFocus;    // m — 여기까지는 또렷하다
uniform float uRange;    // m — 이만큼 더 가면 완전히 흐리다
uniform float uMaxR;     // 최대 흐림 반지름 (장치 픽셀)
uniform int   uTaps;
uniform int   uDebug;    // 0 그림 · 1 흐림세기 · 2 거리
out vec4 fragColor;

float dist_m(vec2 uv){
  float zw = texture(uDepth, uv).r;
  if (zw >= uDMax) return 1.0e9;             // 하늘 — 깊이가 안 쓰인 곳
  float zn = 2.0*(zw/uDMax) - 1.0;
  return 2.0*uNear*uFar / (uFar + uNear - zn*(uFar - uNear)) * uMPU;
}
float coc(float d){ return clamp((d - uFocus)/uRange, 0.0, 1.0); }

void main(){
  vec2 uv = gl_FragCoord.xy * uPix;
  float d0 = dist_m(uv);
  float c0 = coc(d0);
  if (uDebug == 1){ fragColor = vec4(vec3(c0), 1.0); return; }
  if (uDebug == 2){ fragColor = vec4(vec3(clamp(d0/1500.0,0.0,1.0)), 1.0); return; }

  vec3 sum = texture(uColor, uv).rgb; float wsum = 1.0;
  float r = c0 * uMaxR;
  if (r > 0.6) {
    const float ga = 2.39996323;             // 황금각 — 고르게 퍼지는 원반 표본
    for (int i=0;i<64;i++){
      if (i>=uTaps) break;
      float t = (float(i)+0.5)/float(uTaps);
      float rad = sqrt(t)*r;
      float a = float(i)*ga;
      vec2 su = uv + vec2(cos(a),sin(a))*rad*uPix;
      float cs = coc(dist_m(su));
      // 🔴 가까운(또렷한) 픽셀을 먼 곳으로 끌고 오지 않는다.
      //    안 막으면 건물 실루엣 둘레에 후광이 생긴다.
      float w = step(c0*0.55, cs);
      sum += texture(uColor, su).rgb * w; wsum += w;
    }
  }
  fragColor = vec4(sum/wsum, 1.0);
}`;

function compile(gl, type, src) {
  const s = gl.createShader(type);
  gl.shaderSource(s, src); gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
  return s;
}

// Z 단위 하나가 몇 m 인가. MapLibre 의 3D 좌표는 "지금 확대에서의 화면 픽셀" 이라
// 위도와 확대로 정해진다.
export function metersPerUnit(map) {
  const lat = map.getCenter().lat;
  return 156543.03392804097 * Math.cos(lat * Math.PI / 180) / Math.pow(2, map.getZoom());
}

export function installDof(map, opts = {}) {
  const S = window.__dof = Object.assign({
    on: true, focus: 120, range: 900, maxR: 6, taps: 16, debug: 0,
    // 🔴 확대가 이만큼 되기 전에는 **아예 안 돈다** (복사도 안 한다).
    //    항공 시점에서 심도를 걸면 "장난감 도시" 가 되고, 값도 그냥 버리는 것이다.
    zoomStart: 16.5, zoomFull: 17.5,
    fbStatus: null, blitErr: null, lastErr: 0, frames: 0,
  }, opts);

  const layer = {
    id: 'dof', type: 'custom', renderingMode: '3d',
    onAdd(m, gl) {
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, VS));
      gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, FS));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
      this.prog = p; this.u = {};
      for (const n of ['uColor','uDepth','uPix','uDMax','uNear','uFar','uMPU','uFocus','uRange','uMaxR','uTaps','uDebug'])
        this.u[n] = gl.getUniformLocation(p, n);
      this.vao = gl.createVertexArray();
      this.w = 0; this.h = 0;
    },
    _size(gl, w, h) {
      if (this.w === w && this.h === h) return;
      this.w = w; this.h = h;
      if (this.fbo) { gl.deleteFramebuffer(this.fbo); gl.deleteTexture(this.tc); gl.deleteTexture(this.td); }
      this.tc = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, this.tc);
      gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA8, w, h);
      for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR],
                            [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]])
        gl.texParameteri(gl.TEXTURE_2D, k, v);
      // 🔴 깊이는 **기본 프레임버퍼와 같은 형식**이어야 복사가 된다.
      //    MapLibre 는 depth:true·stencil:true 로 컨텍스트를 만들어서 DEPTH24_STENCIL8 이다.
      this.td = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, this.td);
      gl.texStorage2D(gl.TEXTURE_2D, 1, gl.DEPTH24_STENCIL8, w, h);
      for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST],
                            [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]])
        gl.texParameteri(gl.TEXTURE_2D, k, v);
      this.fbo = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.fbo);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, this.tc, 0);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_STENCIL_ATTACHMENT, gl.TEXTURE_2D, this.td, 0);
      S.fbStatus = gl.checkFramebufferStatus(gl.FRAMEBUFFER) === gl.FRAMEBUFFER_COMPLETE ? 'complete' : 'INCOMPLETE';
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    },
    render(gl, args) {
      if (!S.on) return;
      // 확대에 따라 서서히 켠다. 0 이면 한 줄도 안 돈다.
      const z = map.getZoom();
      const ramp = Math.max(0, Math.min(1, (z - S.zoomStart) / (S.zoomFull - S.zoomStart)));
      if (ramp <= 0 && !S.debug) return;
      const maxR = S.maxR * (S.debug ? 1 : ramp);
      const w = gl.drawingBufferWidth, h = gl.drawingBufferHeight;
      this._size(gl, w, h);
      gl.getError();
      // ① 화면 버퍼(색 + 깊이)를 통째로 우리 텍스처로 복사
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null);
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, this.fbo);
      gl.blitFramebuffer(0,0,w,h, 0,0,w,h,
        gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT | gl.STENCIL_BUFFER_BIT, gl.NEAREST);
      S.blitErr = gl.getError();
      // ② 화면에 다시 한 장 덮어 그린다
      gl.bindFramebuffer(gl.READ_FRAMEBUFFER, null);
      gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER, null);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null);
      gl.viewport(0, 0, w, h);
      gl.disable(gl.DEPTH_TEST); gl.depthMask(false);
      gl.disable(gl.BLEND); gl.disable(gl.STENCIL_TEST); gl.disable(gl.CULL_FACE);
      gl.useProgram(this.prog);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.tc);
      gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, this.td);
      gl.uniform1i(this.u.uColor, 0);
      gl.uniform1i(this.u.uDepth, 1);
      gl.uniform2f(this.u.uPix, 1/w, 1/h);
      gl.uniform1f(this.u.uDMax, map.painter.depthRangeFor3D[1]);
      gl.uniform1f(this.u.uNear, args.nearZ);
      gl.uniform1f(this.u.uFar, args.farZ);
      gl.uniform1f(this.u.uMPU, metersPerUnit(map));
      gl.uniform1f(this.u.uFocus, S.focus);
      gl.uniform1f(this.u.uRange, S.range);
      gl.uniform1f(this.u.uMaxR, maxR);
      gl.uniform1i(this.u.uTaps, S.taps);
      gl.uniform1i(this.u.uDebug, S.debug);
      gl.bindVertexArray(this.vao);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.bindVertexArray(null);
      gl.activeTexture(gl.TEXTURE0);
      S.lastErr = gl.getError();
      S.frames++;
    },
  };
  map.addLayer(layer);
  window.__dofLayer = layer;
  return S;
}
