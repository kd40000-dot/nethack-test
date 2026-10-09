# NetHack Tutorial Mode — repeatable test signing (no user setup)

The APKs published on the `tutorial-preview` release are now signed by the
same **project-specific stable TEST certificate**.

Certificate SHA-256:
`AB:65:AA:9D:D3:98:43:87:34:6D:15:CD:C2:0F:38:84:87:45:7A:32:AC:BA:5E:93:F6:5D:11:4B:EB:4B:D1:DC`

- The keystore is committed to the public repository specifically to avoid manual
  setup and allow unattended, reproducible hobby-test builds.
- It is **NOT a private production signing key**. A public signing key offers
  no protection against a malicious party signing a different APK with the same
  certificate. Only install APKs from a trusted source; use a separately held
  private signing key for any security-sensitive release.
- Never rotate this certificate silently: Android package updates require the
  same package name, compatible certificate, and non-decreasing versionCode.
- APKs v50030 and v50031 were signed by separate temporary CI debug
  certificates. **The transition to v50032 cannot be an ordinary in-place update**
  over those APKs. Back up saves and settings before performing the one-time
  migration, especially on rooted devices. Do not clear data casually.
- Once v50032 is installed, future versions using this same pinned certificate
  can update normally without reinstalling or reentering signing configuration.
- The offline tutorial app does not integrate with GitHub at runtime.
