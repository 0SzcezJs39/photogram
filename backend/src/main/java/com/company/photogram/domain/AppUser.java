package com.company.photogram.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Пользователь приложения
 */
@Entity
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable=false, updatable=false)
    private Long id;
    
    @Column(nullable=false, unique=true)
    private String username;
    
    @Column(nullable=false)
    private String password;

    // без него падает
    public AppUser() {}

    public AppUser(String username, String password) {
//        super();
        this.username = username;
        this.password = password;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    @JsonIgnore
    public String getPassword() { return password; }

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
}
