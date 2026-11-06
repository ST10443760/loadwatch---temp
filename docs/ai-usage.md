# Declaration of AI Usage and Human-AI Collaboration

## What the AI did

I used Claude and ChatGPT throughout the development of LoadWatch as supporting tools for planning, troubleshooting, documentation and selected supporting assets. The majority of the Kotlin Android application and ASP.NET Core API implementation was carried out by me, with development progressing through smaller batches and frequent commits pushed to GitHub.

AI also assisted with selected supporting components, including the Google sign-in and biometric unlock implementation, offline synchronisation, push notifications, outage history functionality, isiZulu and Afrikaans translations, the application icon and unit tests.

- **Planning:** Claude (chat) helped break down my Part 1 design into manageable development batches
- **Troubleshooting:** Claude assisted in interpreting errors and investigating issues involving Azure, GitHub Actions and Firebase, including a failed container deployment and a timing-related test failure in CI.
- **Documentation:** Claude drafted the README, as well as the release notes

## What I did

- Selected the project, features and architecture based on my original Part 1 design. I also made key technical decisions, including moving to an Azure container deployment after the publish-profile approach proved unsuccessful.
- Personally configured Firebase, Azure SQL, Azure App Service, GitHub and the application's signing key through their respective consoles.
- Built and tested the application on a physical Android device, manually verifying functionality, including offline synchronisation using airplane mode.
- Identified issues that AI-assisted troubleshooting had initially overlooked. For example, I discovered that an offline report appeared to save successfully because a USB tunnel was still providing connectivity.
- Evaluated proposed solutions, decided which changes to implement, performed retesting and managed the GitHub commit and push process.

## How I checked the output

Verification was an important part of the development process:

- 178 Android unit tests and 77 API integration tests, executed through GitHub Actions.
- Lint checks and release builds.
- Azure log streams and Firebase and database consoles to verify actual application behaviour and persisted data.
- Manual testing on physical hardware, including offline scenarios.

The isiZulu and Afrikaans translations were AI-generated. I have not had them independently verified by a fluent speaker.

## Limitations and accountability

AI-generated output is not necessarily accurate or reliable, and mistakes occurred during development. For example, an API integration test failed in CI due to an incorrect timing assumption. I remained responsible for evaluating suggestions, identifying issues, verifying implementations and ensuring the quality of the submitted work.

## References

[1] Anthropic, "Claude," AI assistant, 2026. [Online]. Available: [https://claude.ai](https://claude.ai). [Accessed: Nov. 6, 2026].

[2] Anthropic, "Claude Code," AI coding agent, 2026. [Online]. Available: [https://claude.com/claude-code](https://claude.com/claude-code). [Accessed: Nov. 6, 2026].
