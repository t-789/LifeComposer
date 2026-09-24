package org.example.lifecomposer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    /** Optional for older API clients; required by the new public registration page. */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String realName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 50, message = "密码长度必须在1-50个字符之间")
    private String password;
}
