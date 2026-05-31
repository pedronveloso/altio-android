## Design Context

### Users
Privacy-conscious power users — technically capable but not developers. They run Altio Service because they want on-device inference without cloud dependency. They care about data sovereignty, reliability, and self-sufficiency. Usage pattern: infrequent check-ins to configure the server, manage models, generate API tokens, adjust inference parameters. The app is mostly invisible (running in background) but must feel trustworthy and capable when they do open it.

### Brand Personality
Three words: **Reliable · Private · Capable**

The app is quietly powerful — it runs a local inference server on a phone, which is technically impressive. The interface should reflect that without boasting about it. Tone: calm, precise, professional. Not playful, not corporate, not a prototype. Something you'd be comfortable handing to a technically-curious non-developer and saying "this is how you configure it."

### Aesthetic Direction
Polished consumer-app quality, grounded and information-precise. Clean layout with intentional visual hierarchy, good breathing room, and clear state communication. Feels more like a well-crafted tool (think: 1Password, Tailscale, WireGuard) than a generic settings page or a flashy demo.

**Theme**: Android Dynamic Color — intentional, not default. Lets the system wallpaper harmonize with the app, which is appropriate for an always-running background service. Supports both light and dark mode (follows system).

**Typography**: M3 type scale applied with more deliberate hierarchy than the current implementation — headings, section labels, body copy, and supporting text need stronger differentiation.

**Anti-references**: Generic Android settings pages (gray dividers, uniform row height, no hierarchy). AI slop dashboards (hero metrics, gradient text, neon accents). Over-engineered "developer tool" UIs that feel like a config file.

### Design Principles
1. **Hierarchy over uniformity** — Not every setting row should look the same. Group, section, and weight information to guide the eye.
2. **State clarity** — The server is either running or not. A model is either ready, downloading, or missing. Make these states immediately legible without hunting for text.
3. **Trust through restraint** — A quiet, precise interface communicates reliability. Don't add decoration that doesn't carry information.
4. **Mobile-first density** — Power users expect information density; don't waste vertical space. But ensure touch targets are comfortable, not cramped.
5. **Progressive disclosure** — The main settings surface shows what matters now. Destructive or rare actions (revoke token, delete model) are accessible but not prominent.
