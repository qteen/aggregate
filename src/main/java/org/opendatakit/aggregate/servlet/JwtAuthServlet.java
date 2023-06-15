package org.opendatakit.aggregate.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.common.security.Realm;
import org.opendatakit.common.security.User;
import org.opendatakit.common.security.UserService;
import org.opendatakit.common.web.CallingContext;
import org.opendatakit.common.web.constants.HtmlConsts;

import javax.crypto.SecretKey;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtAuthServlet extends ServletUtilBase {
    public static final String ADDR = "jwtAuth";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CallingContext cc = ContextFactory.getCallingContext(this, req);
        Realm currentRealm = cc.getUserService().getCurrentRealm();

        if (getOpenRosaVersion(req) != null) {
            // OpenRosa implementation
            addOpenRosaHeaders(resp);
            String username = req.getParameter("username");

            SecretKey secretKey = Keys.hmacShaKeyFor(currentRealm.getRealmSecret().getBytes(StandardCharsets.UTF_8));
            String jwt = Jwts.builder()
                    .signWith(secretKey, SignatureAlgorithm.HS256)
                    .setHeaderParam("typ", "JWT")
                    .setIssuer(currentRealm.getRealmString())
                    .setAudience("Moda Survey")
                    .setSubject(username)
                    .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                    .setIssuedAt(new Date())
                    .compact();

            resp.setContentType(HtmlConsts.RESP_TYPE_JSON);
            PrintWriter out = resp.getWriter();
            out.write(new ObjectMapper().writeValueAsString(jwt));
            out.flush();
            out.close();
        }
    }
}
