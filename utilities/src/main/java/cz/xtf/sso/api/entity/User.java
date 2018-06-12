package cz.xtf.sso.api.entity;

public class User {

	public static User getAndroid(int id) {
		return getAndroid(id, "x");
	}

	public static User getAndroid(int id, String uniqueIdentifier) {
		String username = String.format("android-%s-%d", uniqueIdentifier, id);
		String password = String.format("pass-%s-%d", uniqueIdentifier, id);
		String firstname = String.format("Bender-%s-%d", uniqueIdentifier, id);
		String lastname = String.format("Rodriguez-%s-%d", uniqueIdentifier, id);
		String email = String.format("death-%s-%d@toallhumans.com", uniqueIdentifier, id);

		return new User(username, password, firstname, lastname, email);
	}

	public String id;
	public String username;
	public String password;
	public String firstName;
	public String lastName;
	public String email;

	public User(String username, String password, String firstName, String lastName, String email) {
		this.username = username;
		this.password = password;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
	}
}
