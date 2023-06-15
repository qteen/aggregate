package org.opendatakit.common.security.spring;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.common.security.Realm;
import org.opendatakit.common.web.CallingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthFilter extends GenericFilterBean {
    private static final Log logger = LogFactory.getLog(JwtAuthFilter.class);

    private UserDetailsService userDetailsService;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        CallingContext cc = ContextFactory.getCallingContext(getServletContext(), request);
        Realm currentRealm = cc.getUserService().getCurrentRealm();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            UsernamePasswordAuthenticationToken authentication = parseToken(request, currentRealm.getRealmSecret());

            if(auth==null && authentication!=null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        } catch (JwtException e) {
            SecurityContextHolder.getContext().setAuthentication(null);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            e.printStackTrace();
        }

        chain.doFilter(req, res);
    }

    private UsernamePasswordAuthenticationToken parseToken(HttpServletRequest request, String secret) throws JwtException {
        String token = request.getHeader("X-Moda-Auth-Token");

        if(token != null) {
            Jws<Claims> claimsJws = Jwts.parserBuilder()
                    .setSigningKey(secret)
                    .build()
                    .parseClaimsJws(token);

            String username = claimsJws.getBody().getSubject();
            if ("".equals(username) || username == null) {
                return null;
            }

            UserDetails user = this.userDetailsService
                    .loadUserByUsername(username);

            return new UsernamePasswordAuthenticationToken(username, user.getPassword(), null);
        }

        return null;
    }

    public UserDetailsService getUserDetailsService() {
        return userDetailsService;
    }

    public void setUserDetailsService(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }
}
