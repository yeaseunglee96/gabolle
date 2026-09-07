// 바다에 파도를 넣는다.
//
// 🔴 이건 three.js 가 아니다. 지도 라이브러리가 열어 주는 "직접 그리는 자리"(커스텀 레이어)에
//    WebGL 을 그대로 쓴다. three.js 는 WebGL 을 쉽게 쓰게 해 주는 도구일 뿐이고,
//    이 정도 일에는 도구가 필요 없다. 외부 의존성 0 이다.
//
// 어떻게 되는가 — **바탕 지도가 아는 물 모양 그대로** 삼각형을 깔고, 그 위 픽셀마다 물결을 계산한다.
//
// 🔴 처음에는 해수면 높이에 큰 판 하나를 깔고 "땅은 그보다 높으니 지형이 알아서 가려 주겠지"에
//    기댔다. 그게 틀렸다. 땅 높이 자료가 **매립지를 해수면과 같게 적어 두기 때문**이다 —
//    마린시티를 물어보면 0.005 m 가 나온다(해운대 해변은 10 m 로 제대로 나온다). 그래서
//    마린시티가 통째로 물에 잠겼고, 눕힐수록 깊이 정밀도가 무너져 판과 땅이 번갈아 이기며
//    깜빡였다. **깊이 조절로는 못 고치는 문제다. 물이 아닌 곳에는 아예 안 그려야 한다.**
//
// 비용 — 파도는 **픽셀당 계산**이라 건물 수와 무관하다. 물결 4개 + 하늘 반사 + 햇빛 반짝임이
// 픽셀당 40회쯤이고, 폰에서 바다가 화면의 절반이어도 한 프레임에 1 ms 아래다.
// 비싼 것은 "진짜 반사"(장면을 한 번 더 그리는 것)인데 그건 안 한다 — 하늘색을 각도에 따라
// 섞는 것으로 눈은 충분히 속는다.
//
// 물 모양은 바탕 지도의 벡터 타일에서 꺼낸다 — 화면에 보이는 만큼만, 폴리곤 100여 개에
// 꼭짓점 5천 개 안팎이다. 삼각형으로 쪼개는 데 몇 ms 면 끝난다.
//
// 섬(동백섬 등)은 물 모양에서 구멍으로 들어 있는데 그 구멍을 무시한다. 섬은 지형이 높아서
// 깊이 검사가 알아서 가려 주기 때문이다 — 여기서는 깊이에 기대도 된다. 높이 차가 크니까.

const VERT = `#version 300 es
precision highp float;
uniform mat4 u_matrix;
uniform vec3 u_origin;
uniform float u_scale;      // 메르카토르 1 단위가 몇 m 인가
in vec3 a_pos;              // 메르카토르 좌표 (x, y, z)
out vec3 v_world;           // 기준점에서의 거리 (m). x = 동쪽, y = 남쪽, z = 위
void main() {
  v_world = (a_pos - u_origin) * u_scale;
  gl_Position = u_matrix * vec4(a_pos, 1.0);
}`;

const FRAG = `#version 300 es
precision highp float;
in vec3 v_world;
uniform vec3 u_cam;         // 카메라 위치 (m, 같은 기준점)
uniform vec3 u_sun;         // 해가 있는 쪽 (단위 벡터)
uniform float u_time;       // 초
uniform vec3 u_deep;        // 깊은 물 색
uniform vec3 u_sky;         // 하늘이 비치는 색
uniform vec3 u_sunCol;      // 햇빛 색
uniform float u_sunStr;     // 반짝임 세기 (밤이면 0)
uniform float u_amp;        // 물결 세기
uniform float u_alpha;      // 가까이 가면 0 으로 사라진다
out vec4 fragColor;

// 물결 하나가 만드는 수면의 기울기.
// 방향 d 로 파장 L, 속도 s 로 흘러간다.
vec2 slopeOf(vec2 p, vec2 d, float L, float s, float t) {
  float k = 6.2831853 / L;
  return d * cos(dot(p, d) * k + t * s * k);
}

void main() {
  vec3 toCam = u_cam - v_world;
  float dist = length(toCam);
  vec3 V = toCam / max(dist, 1e-4);

  // 🔴 물결마다 **따로** 거리 감쇠를 준다. 하나로 주면 두 가지가 동시에 망가진다:
  //    세게 주면 먼바다의 파도가 통째로 지워지고, 약하게 주면 짧은 물결이 픽셀보다 촘촘해져
  //    지글거린다(모아레). 기준은 "화면에서 한 파장이 몇 픽셀인가" 다.
  //
  // 🔴 그리고 **긴 너울을 넣어야 멀리서 움직임이 보인다.** 처음에는 가장 긴 물결이 42 m 였는데,
  //    700 m 높이에서 5 km 밖을 보면 그게 2~4 픽셀이라 움직여도 눈에 안 띈다. 그래서 파도가
  //    해안선에만 있고 먼바다는 죽은 것처럼 보였다. 340 m 너울은 같은 거리에서 20~40 픽셀이다.
  vec2 p = v_world.xy;
  float t = u_time;
  vec2 slope = vec2(0.0);
  slope += slopeOf(p, normalize(vec2( 0.80,  0.60)), 340.0, 9.0, t) * 1.25 / (1.0 + dist * 0.000030);
  slope += slopeOf(p, normalize(vec2( 0.55,  0.84)), 175.0, 6.5, t) * 0.95 / (1.0 + dist * 0.000070);
  slope += slopeOf(p, normalize(vec2( 0.92,  0.39)),  62.0, 4.6, t) * 0.60 / (1.0 + dist * 0.000180);
  slope += slopeOf(p, normalize(vec2(-0.47,  0.88)),  27.0, 3.3, t) * 0.38 / (1.0 + dist * 0.000600);
  slope += slopeOf(p, normalize(vec2( 0.31, -0.95)),  12.5, 2.5, t) * 0.22 / (1.0 + dist * 0.001800);
  slope += slopeOf(p, normalize(vec2( 0.96,  0.14)),   6.0, 1.9, t) * 0.12 / (1.0 + dist * 0.005200);

  // 바람 자국 — 물 위를 천천히 흘러가는 넓은 얼룩. 높은 데서 바다를 보면 실제로 이게 보인다.
  // 물결이 아니라 **거칠기가 넓게 변하는 것**이라 파장이 아주 길고, 그래서 수평선까지 살아남는다.
  // 물결 몇 개만 더하면 같은 무늬가 반복돼 단조로운데, 이게 그 반복을 깨 준다.
  float streak = sin(dot(p, vec2(0.31, 0.95)) / 260.0 + t * 0.10)
               * sin(dot(p, vec2(0.87, -0.49)) / 430.0 - t * 0.07);
  float rough = 0.70 + 0.30 * streak;

  vec3 n = normalize(vec3(-slope * u_amp * rough, 1.0));

  // 프레넬 — 비스듬히 볼수록 물이 거울이 된다.
  // 반사색으로 수평선 색(거의 흰색)을 그대로 쓰면 바다가 하얗게 뜬다. 밖에서 어둡게 섞어 넘긴다.
  float fres = pow(1.0 - max(dot(n, V), 0.0), 5.0);
  vec3 col = mix(u_deep, u_sky, clamp(fres, 0.0, 1.0) * 0.62);

  // 햇빛 반짝임 — 두 겹이다. 좁고 밝은 것은 가까이서 알갱이처럼 튀고,
  // 넓고 은은한 것은 멀리서 해 쪽으로 **빛나는 띠**로 보인다. 먼바다가 살아 보이는 이유가 이것이다.
  vec3 H = normalize(u_sun + V);
  float nh = max(dot(n, H), 0.0);
  float spec = pow(nh, 220.0) * 1.5 + pow(nh, 24.0) * 0.20;
  col += u_sunCol * spec * u_sunStr * rough;

  // 아주 멀면 대기에 녹아들게 둔다. 수평선이 칼처럼 잘리면 가짜로 보인다.
  col = mix(col, u_sky, clamp((dist - 20000.0) / 30000.0, 0.0, 1.0) * 0.5);

  fragColor = vec4(col, u_alpha);
}`;

// ── 폴리곤을 삼각형으로 쪼갠다 ────────────────────────────────────
// GPU 는 삼각형만 그릴 줄 안다. 물 모양은 구불구불한 다각형이라 잘라 줘야 한다.
//
// 🔴 이걸 직접 짜려다 실패했다. 단순한 "귀 자르기" 는 **자기 자신에 닿는 모양**에서 무너져
//    폴리곤 바깥으로 가느다란 삼각형을 내놓는다 — 화면에서 바다가 육지로 뻗는 가시로 보였다.
//    벡터 타일은 폴리곤을 타일 경계에서 잘라 내보내므로 그런 모양이 흔하다.
//    (무게중심이 안에 있나 검사해도 안 걸렸다. 그 링들이 실제로 그 영역을 감싸고 있기 때문이다.)
//
//    그래서 검증된 것을 쓴다. earcut 은 MapLibre 자신이 쓰는 삼각분할기다.
//    계산기하는 직접 쓰지 않는다 — 맞는 것처럼 보이다가 이런 데서 틀린다.
import earcut from './earcut.mjs?v=0906-1336';

// 폴리곤 하나(바깥 링 + 구멍들)를 삼각형 꼭짓점 목록으로.
function triangulate(rings) {
  const verts = [];
  const holes = [];
  for (let i = 0; i < rings.length; i++) {
    if (i > 0) holes.push(verts.length / 2);
    for (const p of rings[i]) verts.push(p[0], p[1]);
  }
  if (verts.length < 6) return [];
  const idx = earcut(verts, holes, 2);
  const out = [];
  for (const k of idx) out.push([verts[k * 2], verts[k * 2 + 1]]);
  return out;
}

function compile(gl, type, src, label) {
  const sh = gl.createShader(type);
  gl.shaderSource(sh, src);
  gl.compileShader(sh);
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
    throw new Error(`[바다] ${label} 셰이더를 못 만들었습니다:\n` + gl.getShaderInfoLog(sh));
  }
  return sh;
}

/**
 * @param {object} opt
 *   waterRings  지금 화면의 물 모양. 폴리곤마다 [바깥링, 구멍링…] 형태의 배열을 돌려준다
 *   toMerc      경위도 → 메르카토르 좌표 (MercatorCoordinate.fromLngLat)
 *   origin      물결 무늬의 기준점 [경도, 위도]. 화면을 옮겨도 파도가 안 미끄러지게 고정한다
 *   seaLevelM   해발 몇 m 에 둘 것인가. 0 이면 지형과 다퉈 지글거린다
 */
export function makeSeaLayer({ waterRings, toMerc, origin, seaLevelM = 0.3 }) {
  let program = null;
  let vao = null;
  let buf = null;
  let loc = {};
  let originMerc = null;
  let metersPerMerc = 1;
  let vertCount = 0;
  let rebuildTimer = null;

  // 밖에서 바꿔 주는 값들. 시각이 바뀌면 여기만 갱신하면 된다.
  const state = {
    deep: [0.10, 0.20, 0.27],
    sky: [0.62, 0.74, 0.84],
    sunCol: [1.0, 0.93, 0.78],
    sunStr: 1.0,
    sun: [0.0, -0.7, 0.7],
    amp: 0.17,   // 물결 성분이 6개로 늘어 기울기 합이 커졌다. 그만큼 낮춘다
    alpha: 1.0,
    t0: performance.now(),
  };

  const layer = {
    id: 'sea-waves',
    type: 'custom',
    // 🔴 '3d' 여야 깊이 버퍼를 다른 레이어와 함께 쓴다.
    //    그래야 땅과 건물이 바다를 제대로 가린다. '2d' 로 두면 위에 덧칠된다.
    renderingMode: '3d',
    state,

    onAdd(map, gl) {
      const vs = compile(gl, gl.VERTEX_SHADER, VERT, '꼭짓점');
      const fs = compile(gl, gl.FRAGMENT_SHADER, FRAG, '픽셀');
      program = gl.createProgram();
      gl.attachShader(program, vs);
      gl.attachShader(program, fs);
      gl.bindAttribLocation(program, 0, 'a_pos');
      gl.linkProgram(program);
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
        throw new Error('[바다] 프로그램을 못 이었습니다: ' + gl.getProgramInfoLog(program));
      }
      for (const n of ['u_matrix', 'u_origin', 'u_scale', 'u_cam', 'u_sun', 'u_time',
                       'u_deep', 'u_sky', 'u_sunCol', 'u_sunStr', 'u_amp', 'u_alpha']) {
        loc[n] = gl.getUniformLocation(program, n);
      }

      const o = toMerc({ lng: origin[0], lat: origin[1] }, 0);
      originMerc = [o.x, o.y, 0];
      // 메르카토르 1 단위 = 적도 둘레(m). 위도에 따라 실제 거리는 달라지지만
      // 물결 크기를 정하는 데 쓰는 값이라 이 정도로 충분하다.
      metersPerMerc = 40075016.686 * Math.cos((origin[1] * Math.PI) / 180);

      vao = gl.createVertexArray();
      gl.bindVertexArray(vao);
      buf = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, buf);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 0, 0);
      gl.bindVertexArray(null);

      layer._map = map;
      layer._gl = gl;

      // 화면이 멈추면 물 모양을 다시 만든다. 움직이는 동안에는 안 건드린다 —
      // 그림자와 같은 이유다. 바꾸는 순간이 곧 깜빡이는 순간이다.
      const later = () => {
        clearTimeout(rebuildTimer);
        rebuildTimer = setTimeout(() => {
          if (map.isMoving() || map.isZooming() || map.isRotating()) { later(); return; }
          layer.rebuild();
        }, 260);
      };
      map.on('moveend', later);
      map.on('sourcedata', (e) => { if (e.isSourceLoaded) later(); });
      later();
    },

    // 지금 화면의 물 모양을 삼각형으로 만들어 GPU 에 올린다.
    rebuild() {
      const gl = layer._gl;
      if (!gl || !buf) return;
      const t0 = performance.now();
      const polys = waterRings();
      const pts = [];
      for (const rings of polys) {
        for (const p of triangulate(rings)) {
          const m = toMerc({ lng: p[0], lat: p[1] }, seaLevelM);
          pts.push(m.x, m.y, m.z);
        }
      }
      vertCount = pts.length / 3;
      gl.bindBuffer(gl.ARRAY_BUFFER, buf);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(pts), gl.DYNAMIC_DRAW);
      layer.stat = {
        폴리곤: polys.length,
        삼각형: Math.round(vertCount / 3),
        걸린ms: +(performance.now() - t0).toFixed(1),
      };
      layer._map.triggerRepaint();
    },

    render(gl, args) {
      if (!program) return;
      const map = layer._map;

      // 🔴 가까이 가면 파도를 끈다.
      //
      //    해수면 판은 "땅이 해수면보다 높다" 는 것에 기대어 지형이 가려 주기를 바란다.
      //    그런데 매립지(마린시티 같은 곳)는 땅 높이 자료가 판보다 낮게 읽히는 곳이 있어서,
      //    확대하면 **바다가 육지 위로 삐져나온 얼룩**이 생기고 확대할 때마다 이기는 쪽이
      //    바뀌어 깜빡인다. 실제로 확대 17단계에서 그렇게 보였다.
      //
      //    멀리서는 그 얼룩이 한 픽셀도 안 되어 안 보이고, 가까이서는 도시를 보는 것이지
      //    바다를 보는 것이 아니다. 그래서 15.2 단계부터 서서히 지워 16.4 에서 완전히 끈다.
      //    바다 색은 바탕 지도가 계속 칠하고 있으므로 사라져도 바다는 그대로 있다.
      // 이제 물이 아닌 곳에는 도형 자체가 없으므로 확대해도 육지를 덮지 않는다.
      // 확대할 때 파도를 끄던 임시 조치는 필요 없어졌다.
      const alpha = state.alpha;
      if (vertCount === 0) { map.triggerRepaint(); return; }

      // 카메라 위치 — 물이 어느 각도로 보이는지 알아야 프레넬과 반짝임이 계산된다.
      let cam = [0, 0, 1000];
      try {
        const p = map.getFreeCameraOptions().position;
        cam = [
          (p.x - originMerc[0]) * metersPerMerc,
          (p.y - originMerc[1]) * metersPerMerc,
          p.z * metersPerMerc,
        ];
      } catch { /* 못 얻어도 화면은 돈다 */ }

      gl.useProgram(program);
      gl.uniformMatrix4fv(loc.u_matrix, false, args.defaultProjectionData.mainMatrix);
      gl.uniform3fv(loc.u_origin, originMerc);
      gl.uniform1f(loc.u_scale, metersPerMerc);
      gl.uniform3fv(loc.u_cam, cam);
      gl.uniform3fv(loc.u_sun, state.sun);
      gl.uniform1f(loc.u_time, (performance.now() - state.t0) / 1000);
      gl.uniform3fv(loc.u_deep, state.deep);
      gl.uniform3fv(loc.u_sky, state.sky);
      gl.uniform3fv(loc.u_sunCol, state.sunCol);
      gl.uniform1f(loc.u_sunStr, state.sunStr);
      gl.uniform1f(loc.u_amp, state.amp);
      gl.uniform1f(loc.u_alpha, alpha);

      gl.enable(gl.DEPTH_TEST);
      gl.depthFunc(gl.LEQUAL);
      // 깊이를 **쓰지는 않는다.** 바다가 무언가를 가릴 일은 없고, 안 쓰면 지형과의
      // 깊이 싸움이 한쪽으로 정리된다.
      gl.depthMask(false);
      // 같은 깊이일 때는 땅이 이기게 뒤로 살짝 민다. 얼룩의 직접적인 원인이 이 동점이다.
      gl.enable(gl.POLYGON_OFFSET_FILL);
      gl.polygonOffset(1.5, 3.0);
      gl.enable(gl.BLEND);
      gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);

      gl.bindVertexArray(vao);
      gl.drawArrays(gl.TRIANGLES, 0, vertCount);
      gl.bindVertexArray(null);
      gl.disable(gl.POLYGON_OFFSET_FILL);

      // 파도는 가만히 있어도 움직여야 한다. 다음 프레임을 계속 요청한다.
      map.triggerRepaint();
    },

    onRemove(map, gl) {
      if (program) gl.deleteProgram(program);
      if (vao) gl.deleteVertexArray(vao);
      program = null;
      vao = null;
    },
  };

  return layer;
}
