package com.love.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ DB 컬럼: user_id
    @Column(name = "user_id", unique = true, nullable = false, length = 50)
    private String userId;

    // ✅ DB 컬럼: password_hash (기존 DB랑 맞춤)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // ✅ DB 컬럼: name
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // ✅ DB 컬럼: birth_date
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    protected User() {}

    // ✅ 이거 "생성자"가 아니라 "팩토리 메서드"라서 이름 혼동됨 → 그대로 써도 되지만 관례상 static factory
    @Builder
    public static User of(String userId, String passwordHash, String name, LocalDate birthDate) {
        User u = new User();
        u.userId = userId;
        u.passwordHash = passwordHash;
        u.name = name;
        u.birthDate = birthDate;
        return u;
    }
}