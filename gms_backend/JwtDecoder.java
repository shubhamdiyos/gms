import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;

public class JwtDecoder {
    public static void main(String[] args) {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdXBlcmFkbWluIiwicm9sZXMiOlsiU1VQRVJBRE1JTiJdLCJzY2hvb2xJZCI6MSwiaXNzIjoiZ21zIiwiaWF0IjoxNzU2MjEwMDc5LCJleHAiOjE3NTYyMTM2Nzl9.90C6AwgFyE8jm4OlTbavRluy_GncnTEsiFOK6qlRUmI";
        String secret = "change-me-please-change-me-please-change-me-please";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            System.out.println("Claims: " + claims);
            System.out.println("Subject: " + claims.getSubject());
            System.out.println("Roles: " + claims.get("roles"));
            System.out.println("School ID: " + claims.get("schoolId"));
            System.out.println("Employee ID: " + claims.get("empId"));
            System.out.println("Issuer: " + claims.getIssuer());
            System.out.println("Issued At: " + claims.getIssuedAt());
            System.out.println("Expiration: " + claims.getExpiration());
        } catch (Exception e) {
            System.out.println("Error decoding token: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
