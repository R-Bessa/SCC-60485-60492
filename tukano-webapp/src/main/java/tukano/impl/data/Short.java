package tukano.impl.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import tukano.impl.Token;

/**
 * Represents a Short video uploaded by an user.
 * 
 * A short has an unique shortId and is owned by a given user; 
 * Comprises of a short video, stored as a binary blob at some bloburl;.
 * A post also has a number of likes, which can increase or decrease over time. It is the only piece of information that is mutable.
 * A short is timestamped when it is created.
 *
 */
@Entity
public class Short {

	@Id
	@JsonProperty("id")
	@JsonAlias("shortId")
	String shortId;
	String ownerId;
	String blobUrl;
	long timestamp;
	int totalLikes;
	int views;

	public Short() {}

	@JsonCreator
	public Short(@JsonProperty("id") @JsonAlias("shortId") String shortId, @JsonProperty("ownerId") String ownerId,
				 @JsonProperty("blobUrl") String blobUrl, @JsonProperty("timestamp") long timestamp,
				 @JsonProperty("totalLikes") int totalLikes, @JsonProperty("views") int views) {
		this.shortId = shortId;
		this.ownerId = ownerId;
		this.blobUrl = blobUrl;
		this.timestamp = timestamp;
		this.totalLikes = totalLikes;
		this.views = views;
	}

	public Short(String shortId, String ownerId, String blobUrl) {
		this(shortId, ownerId, blobUrl, System.currentTimeMillis(), 0, 0);
	}
	
	public String getShortId() {
		return shortId;
	}

	public void setShortId(String shortId) {
		this.shortId = shortId;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getBlobUrl() {
		return blobUrl;
	}

	public void setBlobUrl(String blobUrl) {
		this.blobUrl = blobUrl;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public int getTotalLikes() {
		return totalLikes;
	}

	public void setTotalLikes(int totalLikes) {
		this.totalLikes = totalLikes;
	}

	public int getViews() {
		return views;
	}

	public void updateViews(int views) {
		this.views += views;
	}

	public void setViews(int views) { this.views = views; }

	@Override
	public String toString() {
		return "Short [shortId=" + shortId + ", ownerId=" + ownerId + ", blobUrl=" + blobUrl + ", timestamp="
				+ timestamp + ", totalLikes=" + totalLikes + ", views=" + views +  "]";
	}

	public Short copyWithLikes_And_Token(long totLikes) {
		String urlWithToken = blobUrl.contains("?token=") ? blobUrl : String.format("%s?token=%s", blobUrl, Token.get(shortId));
		return new Short(shortId, ownerId, urlWithToken, timestamp, (int) totLikes, views);
	}

}