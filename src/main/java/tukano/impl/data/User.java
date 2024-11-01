package tukano.impl.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.Objects;

@Entity
public class User {
	
	@Id
	@JsonProperty("id")
	@JsonAlias("userId")
	private String userId;
	private String pwd;
	private String email;	
	private String displayName;

	public User() {}

	@JsonCreator
	public User(@JsonProperty("id") @JsonAlias("userId") String userId, @JsonProperty("pwd") String pwd,
				@JsonProperty("email") String email, @JsonProperty("displayName") String displayName) {
		this.userId = userId;
		this.pwd = pwd;
		this.email = email;
		this.displayName = displayName;
	}

	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public String getPwd() {
		return pwd;
	}
	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String userId() {
		return userId;
	}
	
	public String pwd() {
		return pwd;
	}
	
	public String email() {
		return email;
	}
	
	public String displayName() {
		return displayName;
	}
	
	@Override
	public String toString() {
		return "User [userId=" + userId + ", pwd=" + pwd + ", email=" + email + ", displayName=" + displayName + "]";
	}
	
	public User copyWithoutPassword() {
		return new User(userId, "", email, displayName);
	}
	
	public User updateFrom( User other ) {
		return new User( userId, 
				other.pwd != null ? other.pwd : pwd,
				other.email != null ? other.email : email, 
				other.displayName != null ? other.displayName : displayName);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof User user)) return false;
		return Objects.equals(getUserId(), user.getUserId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getUserId());
	}
}
