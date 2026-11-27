package com.example.teama_management.form;

public class PasswordChangeForm {
    private String _currently_password;
    private String _new_password;
    private String _confirm_new_password;

    public String get_currently_password() { return _currently_password; }
    public void set_currently_password(String _currently_password) { this._currently_password = _currently_password; }

    public String get_new_password() { return _new_password; }
    public void set_new_password(String _new_password) { this._new_password = _new_password; }

    public String get_confirm_new_password() { return _confirm_new_password; }
    public void set_confirm_new_password(String _confirm_new_password) { this._confirm_new_password = _confirm_new_password; }
}