# Licensing Structure

Altio is licensed under the **GNU Affero General Public License v3.0 or later** (AGPL-3.0-or-later). The full text of the license is available in the [LICENSE](LICENSE) file.

## Plain Language Summary

The AGPL-3.0 is a strong copyleft license. Here is what it means for you in plain language:

1. **Open Source & Copyleft:** If you modify Altio or link it in your software, your derivative work must also be open-source under the AGPL-3.0.
2. **Network/Remote Use:** Unlike the standard GPL, the AGPL triggers source-availability obligations if users interact with a modified version of the software over a network (e.g., if you host Altio on a server or use it as a back-end service). You must make the modified source code available to those users.

## Commercial Licensing

To support organizations that cannot comply with the AGPL-3.0 copyleft terms, the Altio project preserves the option to offer alternative commercial licenses.

Under our Contributor License Agreement (CLA), the project maintainer holds the rights necessary to offer these commercial licenses. If your organization is interested in a commercial license, please contact the maintainer:

* **Maintainer:** Pedro Veloso
* **Contact:** Please contact Pedro Veloso via GitHub or project contact channels.

## Per-Component Licensing Notes

All source code in this repository is licensed under the AGPL-3.0-or-later, unless otherwise noted.

* **Altio App & Libraries (`app`, `core`, `ui`, `server`, `runtime`):** Fully licensed under AGPL-3.0-or-later.
* **Altio Android SDK (`altio-android-sdk`):** Licensed under AGPL-3.0-or-later.
* **Demo App (`demo`):** Licensed under AGPL-3.0-or-later. It serves as a reference client app for the API.

## Frequently Asked Questions (FAQ)

### Can I use Altio in my open-source app?
Yes! As long as your open-source app is compatible with the AGPL-3.0 (e.g., licensed under AGPL-3.0 or a compatible copyleft license), you are free to integrate Altio.

### Can I use Altio in my closed-source app?
This depends on your integration model:

* **Separation via Local API (No Linking):** If your closed-source app interacts with Altio solely over the local HTTP/REST/SSE loopback API (running as a separate process/service on the device) and does not statically or dynamically link to Altio's code, the AGPL copyleft obligations do not extend to your application.
* **Direct Linking or Modification:** If you link Altio's code directly into your closed-source application, or distribute a modified version of the Altio service, you must comply with the AGPL (meaning your app's source code would have to be released under the AGPL) or obtain a commercial license from the maintainer.

### How do I get a commercial license?
Please contact the project maintainer, Pedro Veloso, to discuss licensing terms.
