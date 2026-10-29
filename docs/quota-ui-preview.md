# Quota UI preview

Provisional user-defined quota direction, pending adviser review. This does not mark roadmap D2â€“D6 complete.

The quota flow uses the user-approved light design: off-white background, white surfaces, navy text and accents, navy forecast cards, a circular quota gauge, and outline navigation icons. Authentication keeps the dark navy theme. `QuotaTheme` scopes the palette and system-bar appearance to quota screens; shared inputs and buttons follow the enclosing color scheme. There is no theme toggle.

History is available through the floating bottom navigation. It uses Vico 3.3.1 with illustrative 24-hour, 7-day and quota-cycle data, a total/average summary, peak interval, an exact-readings bottom sheet, and read-only past-cycle details. The 24-hour chart groups readings into 4-hour totals; the exact-readings list retains hourly resolution. Use **Preview options → Preview empty history** to review the empty state. These fixtures are independent of the current sample quota and are not a persistent archive of actions performed on Home.

Notifications open from the bell on Home and History. Sample alerts cover 50%, 80% and 90% milestones, a four-hour forecast reminder, and an informational quota-reached update. These describe an independent, completed sample cycle. Opening an alert marks it read and offers read-only sample cycle details. All/Unread filters, mark-all-read, empty preview and unread badges are implemented. Read flags and fixture timestamps survive recreation through SavedStateHandle; no Room/cloud notification history or OS notification delivery is connected, and no notification permissions are requested. Returning from notifications to History preserves its filters and scroll position.

After signing in, Home replaces the old dashboard placeholder. Create a quota in MB or GB (decimal units) with an optional name. The form creates a sample cycle and returns to the collecting state. Back cancels the form. Draft input survives configuration changes.

Use **Preview options** on Home to review no active quota, collecting usage, forecast available, missing permission, and quota reached. The displayed forecast is a fixture, not inference. Sample quota state survives activity recreation through SavedStateHandle; there is no persistent quota database or history.

Settings is a real navigation destination with a Profile subpage, preview milestone/forecast switches, Usage Access guidance, simulated sync states (Pending/Synced/Offline/Error), version information and preview privacy information. Name edits are validated and saved only to session state; account email is read-only and comes from the existing signed-in session. Preferences are not yet connected to alert scheduling. Manual sync is explicitly simulated and does not upload data. Sign-out requires confirmation, uses existing Supabase Auth, disables repeated submissions while pending and navigates away only on success; failures remain visible in the confirmation dialog. Settings and Profile retain the floating bottom navigation with Settings selected.

Use **View quota details** below the quota summary to see starting amount, used/remaining data, start time, status and forecast. **End cycle** requires confirmation, stops the sample forecast and retains the values on the details screen. Returning Home reflects the ended state. **Create another quota** opens the form; cancelling returns to Details, and submitting starts a fresh sample cycle. Start/end timestamps survive recreation. Selecting a sample scenario clears the ended status for previewing. Creating another quota replaces the session's previous details; historical persistence is deferred.

Usage Access is simulated: **About Usage Access â†’ Preview enabled access** changes only the sample state. No Android permissions, telemetry, background services, backend schema or model changes are included. No quota caps or throttles network traffic.

Compose previews cover all Home states, Create Quota, enlarged fonts and landscape Home. Unit tests cover numeric conversion, invalid input and sample-state restoration; instrumentation tests cover state rendering and form submission.
