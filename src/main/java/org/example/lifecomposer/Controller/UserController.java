package org.example.lifecomposer.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.dto.LoginRequest;
import org.example.lifecomposer.dto.RegisterRequest;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;

    public UserController(UserService userService, UserRepository userRepository, UserDetailsService userDetailsService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        boolean success = userService.register(request.getUsername(), request.getPassword());
        if (!success) {
            return ResponseEntity.badRequest().body("注册失败，用户名可能已存在");
        }
        return ResponseEntity.ok("注册成功");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userService.login(request.getUsername(), request.getPassword());
            if (user == null) {
                return ResponseEntity.badRequest().body("登录失败，用户名或密码错误");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    userDetails.getPassword(),
                    userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("user", user);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext()
            );

            return ResponseEntity.ok("登录成功");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok("登出成功");
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("type", user.getType());
        payload.put("avatar", user.getAvatar());
        payload.put("isBanned", user.getBanned());
        payload.put("banEndTime", user.getBanEndTime());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{userId}/grant-admin")
    public ResponseEntity<?> grantAdmin(@PathVariable int userId, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body("权限不足");
        }
        boolean success = userService.grantAdminPermission(userId);
        return success ? ResponseEntity.ok("用户" + userId + "已被赋予管理员权限")
                : ResponseEntity.badRequest().body("用户" + userId + "不存在");
    }

    @PutMapping("/{userId}/revoke-admin")
    public ResponseEntity<?> revokeAdmin(@PathVariable int userId, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body("权限不足");
        }
        boolean success = userService.revokeAdminPermission(userId);
        return success ? ResponseEntity.ok("用户" + userId + "已被撤销管理员权限")
                : ResponseEntity.badRequest().body("用户" + userId + "不存在");
    }

    @PutMapping("/{userId}/ban")
    public ResponseEntity<?> banUser(@PathVariable int userId,
                                     @RequestBody Map<String, String> payload,
                                     Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body("权限不足");
        }

        String banTime = payload.get("banTime");
        if (banTime == null || banTime.isBlank()) {
            return ResponseEntity.badRequest().body("封禁时间不能为空");
        }

        boolean success = userService.banUser(userId, banTime);
        return success ? ResponseEntity.ok("用户封禁操作完成") : ResponseEntity.badRequest().body("封禁操作失败");
    }

    @PutMapping("/{userId}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable int userId, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body("权限不足");
        }
        boolean success = userService.unbanUser(userId);
        return success ? ResponseEntity.ok("用户解封操作完成") : ResponseEntity.badRequest().body("解封操作失败");
    }

    @PostMapping("/admin/reset-password/{userId}")
    public ResponseEntity<?> adminResetPassword(@PathVariable int userId, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body("权限不足");
        }

        User target = userService.getById(userId);
        if (target == null) {
            return ResponseEntity.badRequest().body("用户不存在");
        }

        boolean success = userService.resetPassword(userId, "000000");
        return success ? ResponseEntity.ok("密码重置成功，新密码为: 000000")
                : ResponseEntity.badRequest().body("密码重置失败");
    }

    private boolean isNotAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }
        User user = userRepository.findByUsername(authentication.getName());
        return user == null || user.getType() == null || user.getType() != UserType.ADMIN;
    }
}
