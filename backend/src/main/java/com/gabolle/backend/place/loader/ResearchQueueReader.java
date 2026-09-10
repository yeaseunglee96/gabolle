package com.gabolle.backend.place.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 조사 대기열(NDJSON)을 {@link SbizRow} 로 읽는다 — S15P21E201-804.
 *
 * <p>대기열은 {@code bigData} 쪽이 부산 음식점 53,716곳에서 <b>2,355곳을 골라</b> 만든 목록이고
 * ({@code process/select-2000.mjs} 2,000곳 + 채점용 355곳), 그 2,355곳의 조사가 끝났다
 * ({@code S15P21E201-760}). 한 줄이 한 가게다.
 *
 * <pre>
 * {"id":"MA0101202511A0024557","name":"편의방","branch":null,
 *  "roadAddr":"부산광역시 중구 해관로 64-1","gu":"중구","hdong":"중앙동",
 *  "category":"중국집","lon":129.035712861823,"lat":35.1046173038424}
 * </pre>
 *
 * <h2>왜 82MB CSV 대신 이 파일을 읽나</h2>
 * 같은 2,355곳에 대해 {@link SbizCsvReader} 가 뽑아 쓰는 칸을 이 파일이 <b>이미 전부</b> 갖고
 * 있다(2,355행에 빠진 칸 0개를 확인했다). 원본 상가정보 CSV 는 82MB 이고 저장소에 없어서
 * 서버에서 가져와야 하는데, 이 파일은 547KB 다.
 *
 * <h2>🔴 열쇠가 같다 — 그래서 나중에 전체를 적재해도 겹치지 않는다</h2>
 * 대기열의 {@code id} 는 <b>상가업소번호</b>이고, 그것을 고른 스크립트
 * ({@code process/select-2000.mjs} 의 {@code COL.storeId = 0})와 이 저장소의 CSV 판독기
 * ({@link SbizCsvReader#COL_STORE_ID})가 같은 칸을 가리킨다. 장소 id 는 그 번호에서 계산해서
 * 만들므로({@code SbizPlaceLoader.placeIdOf}) 나중에 상가정보 전체를 적재해도 <b>같은 가게가
 * 두 행이 되지 않는다.</b> 두 적재기가 만나는 지점이 이 한 가지 사실이다.
 *
 * <h2>이 판독기가 안 하는 것</h2>
 * 조사 <b>결과</b>(왜 사람들이 가는지·현지인인지·줄 서는지)는 읽지 않는다. 그것은 대기열이
 * 아니라 {@code research/data/results/} 에 따로 있고, 그 값을 점수에 어떻게 먹일지는 채점
 * 기준을 정하는 일이라 별도 티켓이다. 여기서는 <b>장소가 있다</b> 까지만 옮긴다.
 */
public final class ResearchQueueReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResearchQueueReader() {
    }

    /**
     * 대기열을 덩어리 단위로 읽어 넘긴다.
     *
     * @param queue {@code research/data/queue.ndjson} 의 경로
     * @param chunkSize 한 번에 넘길 줄 수
     * @param sink 덩어리를 받는 쪽. 보통 {@code SbizPlaceLoader::saveChunk}
     * @return 무엇을 읽고 무엇을 버렸는지
     */
    public static Counts read(Path queue, int chunkSize, Consumer<List<SbizRow>> sink) {
        int total = 0;
        int usable = 0;
        int skippedNoCoordinate = 0;
        int skippedBroken = 0;
        List<SbizRow> chunk = new ArrayList<>(chunkSize);

        try (BufferedReader reader = Files.newBufferedReader(queue, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                total++;
                SbizRow row = parse(line);
                if (row == null) {
                    skippedBroken++;
                    continue;
                }
                if (row.lat() == 0.0 || row.lng() == 0.0) {
                    // 0 은 좌표가 아니라 "안 적혔다" 다. 거리 점수가 그 값을 그대로 믿으면
                    // 아프리카 앞바다에 있는 가게가 부산 한복판보다 가깝게 계산된다.
                    skippedNoCoordinate++;
                    continue;
                }
                usable++;
                chunk.add(row);
                if (chunk.size() >= chunkSize) {
                    sink.accept(List.copyOf(chunk));
                    chunk.clear();
                }
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("조사 대기열을 읽지 못했다: " + queue.toAbsolutePath(), exception);
        }
        if (!chunk.isEmpty()) {
            sink.accept(List.copyOf(chunk));
        }
        return new Counts(total, usable, skippedNoCoordinate, skippedBroken);
    }

    /**
     * 한 줄을 {@link SbizRow} 로 옮긴다. 필수 칸이 없으면 {@code null} — 부르는 쪽이 세어 남긴다.
     *
     * <p>🔴 {@code roadAddr} 이 비면 그 줄을 버리지 않고 <b>빈 주소로 넣는다.</b> 주소는 화면에
     * 보여 줄 값이지 추천에 쓰는 값이 아니라서, 없다고 가게 자체를 없는 것으로 만들 이유가 없다.
     * 좌표와 달리 이 칸은 틀린 계산을 만들지 않는다.
     */
    private static SbizRow parse(String line) {
        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        }
        catch (RuntimeException exception) {
            return null;
        }
        String id = text(node, "id");
        String name = text(node, "name");
        String category = text(node, "category");
        if (id.isBlank() || name.isBlank() || category.isBlank()) {
            return null;
        }
        JsonNode lat = node.get("lat");
        JsonNode lng = node.get("lon");
        if (lat == null || lng == null || !lat.isNumber() || !lng.isNumber()) {
            return null;
        }
        return new SbizRow(id, name, text(node, "branch"), category, text(node, "roadAddr"),
                lat.doubleValue(), lng.doubleValue());
    }

    /** 없는 칸과 {@code null} 을 빈 문자열로 맞춘다 — {@code SbizRow.branch} 계약과 같다. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? "" : value.asString();
    }

    /**
     * 읽은 결과.
     *
     * <p>🔴 버린 것을 갈라서 센다. 합계만 남기면 "2,355줄 중 2,300곳을 넣었다" 를 보고도 나머지
     * 55곳이 좌표가 없어서인지 파일이 깨져서인지 알 수 없고, 그 둘은 해야 할 일이 다르다.
     */
    public record Counts(int total, int usable, int skippedNoCoordinate, int skippedBroken) {

        @Override
        public String toString() {
            return "읽은 줄 " + this.total + " · 쓸 수 있는 " + this.usable
                    + " · 좌표 없어 버린 " + this.skippedNoCoordinate
                    + " · 형식이 깨져 버린 " + this.skippedBroken;
        }
    }
}
