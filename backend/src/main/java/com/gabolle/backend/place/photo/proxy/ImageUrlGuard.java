package com.gabolle.backend.place.photo.proxy;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 대신 받아도 되는 주소인지 가린다(S15P21E201-1954).
 *
 * <p>이 경로는 로그인 없이 열려 있어서, 아무 주소나 받게 두면 남이 우리 서버를 시켜 내부망이나
 * 다른 서버를 두드리게 만들 수 있다(SSRF — 서버 쪽 요청 위조). 그래서 셋을 모두 본다.
 * <ol>
 *   <li>https 이고 사용자 정보·다른 포트가 없다</li>
 *   <li>호스트가 허락 목록과 <b>정확히</b> 같다</li>
 *   <li>그 이름이 가리키는 주소가 내부망·자기 자신이 아니다(허락 호스트의 DNS 가 바뀌어도 막는다)</li>
 * </ol>
 * 리디렉트를 따라갈 때도 같은 검사를 다시 한다 — 허락 호스트가 다른 곳으로 보내는 우회를 막는다.
 */
public class ImageUrlGuard {

	/** 이름을 주소로 바꾼다. 시험이 바꿔 끼운다. */
	@FunctionalInterface
	public interface HostResolver {

		InetAddress[] resolve(String host) throws UnknownHostException;
	}

	/** 거절. 화면에는 400 으로 나간다. */
	public static class Rejected extends RuntimeException {

		public Rejected(String message) {
			super(message);
		}
	}

	private final Set<String> allowedHosts;

	private final HostResolver resolver;

	public ImageUrlGuard(List<String> allowedHosts, HostResolver resolver) {
		this.allowedHosts = allowedHosts.stream()
				.map((h) -> h.trim().toLowerCase(Locale.ROOT))
				.filter((h) -> !h.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
		this.resolver = resolver;
	}

	/** 사람이 보낸 주소 글자를 검사해 URI 로 돌려준다. */
	public URI check(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new Rejected("주소가 없다");
		}
		URI uri;
		try {
			uri = new URI(raw.trim());
		}
		catch (URISyntaxException e) {
			throw new Rejected("주소 형식이 틀렸다");
		}
		return check(uri);
	}

	public URI check(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new Rejected("https 만 받는다");
		}
		if (uri.getRawUserInfo() != null) {
			throw new Rejected("사용자 정보가 든 주소는 받지 않는다");
		}
		if (uri.getPort() != -1 && uri.getPort() != 443) {
			throw new Rejected("다른 포트는 받지 않는다");
		}
		String host = (uri.getHost() == null) ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!this.allowedHosts.contains(host)) {
			throw new Rejected("허락하지 않은 호스트다");
		}
		InetAddress[] addresses;
		try {
			addresses = this.resolver.resolve(host);
		}
		catch (UnknownHostException e) {
			throw new Rejected("호스트를 찾지 못했다");
		}
		if (addresses.length == 0) {
			throw new Rejected("호스트를 찾지 못했다");
		}
		for (InetAddress address : addresses) {
			if (isInternal(address)) {
				throw new Rejected("내부망 주소를 가리킨다");
			}
		}
		return uri;
	}

	static boolean isInternal(InetAddress a) {
		if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
				|| a.isMulticastAddress()) {
			return true;
		}
		byte[] b = a.getAddress();
		if (a instanceof Inet4Address) {
			int first = b[0] & 0xff;
			int second = b[1] & 0xff;
			// 100.64.0.0/10 — 통신사 공용 NAT. 클라우드 내부 주소로도 쓰인다.
			if (first == 100 && second >= 64 && second <= 127) {
				return true;
			}
			// 0.0.0.0/8 · 브로드캐스트
			return first == 0 || (first == 255);
		}
		if (a instanceof Inet6Address) {
			int first = b[0] & 0xff;
			// fc00::/7 — IPv6 사설(고유 지역) 주소
			return (first & 0xfe) == 0xfc;
		}
		return false;
	}
}
