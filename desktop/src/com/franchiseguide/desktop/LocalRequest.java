package com.franchiseguide.desktop;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;

/** Reject ambiguous security headers before taking a first value. */
final class LocalRequest {
    private LocalRequest() {}
    static boolean ambiguousHeaders(HttpExchange exchange) {
        for (String name : List.of("Host", "Origin", "Sec-Fetch-Site", "Authorization",
                "X-FranchiseGuide-Client", "X-FranchiseGuide-Services")) {
            List<String> values=exchange.getRequestHeaders().get(name);
            if(values!=null&&values.size()!=1)return true;
        }
        return false;
    }
}
