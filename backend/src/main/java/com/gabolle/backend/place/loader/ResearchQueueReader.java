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
 * 조사 대기열(NDJSON)을 {@link SbizRow} 로 읽는다. 한 줄이 한 가게다.
 *
 * <pre>
 * {"id":"MA0101202511A0024557","name":"편의방","branch":null,
 *  "roadAddr":"부산광역시 중구 해관로 64-1","gu":"중구","hdong":"중앙동",
 *  "category":"중국집","lon":129.035712861823,"lat":35.1046173038424}
 * </pre>
 *
 * <p>원본 CSV 대신 이 파일을 읽는 것은 {@link SbizCsvReader} 가 뽑아 쓰는 칸을 이 파일이 이미
 * 전부 갖고 있고, 원본은 저장소에 없어 서버에서 가져와야 하기 때문이다.
 *
 * <p>대기열의 {@code id} 는 상가업소번호이고 CSV 판독기가 읽는 칸과 같다. 장소 id 를 그
 * 번호에서 계산하므로 나중에 상가정보 전체를 적재해도 같은 가게가 두 행이 되지 않는다.
 *
 * <p>조사 결과(왜 사람들이 가는지·현지인인지)는 읽지 않는다. 그것은 대기열이 아니라 다른
 * 파일에 있고, 점수에 어떻게 먹일지는 채점 기준을 정하는 일이다. 여기서는 장소가 있다까지만
 * 옮긴다.
 */
public final class ResearchQueueReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResearchQueueReader() {
    }

    /** 대기열을 덩어리 단위로 읽어 {@code sink} 에 넘긴다. */
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
     * {@code roadAddr} 이 비면 버리지 않고 빈 주소로 넣는다. 주소는 화면에 보여 줄 값이라 좌표와
     * 달리 틀린 계산을 만들지 않는다.
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
     * 버린 것을 갈라서 센다. 합계만 남기면 빠진 줄이 좌표가 없어서인지 파일이 깨져서인지 알 수
     * 없고, 그 둘은 해야 할 일이 다르다.
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
