// DATAra — sign in with a username instead of an email.
//
// Supabase Auth only accepts an email (or phone) with a password, so this function looks up the
// email for the username with the service role and signs in on the server. The client receives
// the session tokens only; the email never leaves the server, so the function cannot be used to
// turn a username into someone's address.
//
// Every failure the caller could learn from (unknown username, wrong password) returns the same
// `invalid_credentials` error, matching what the email login path reports.
//
// Deployed with JWT verification off (see config.toml): callers are signed out by definition.

import { createClient } from "npm:@supabase/supabase-js@2.49.4";

const USERNAME_PATTERN = /^[a-z0-9_]{3,20}$/;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const invalidCredentials = () =>
  json(400, { error: "invalid_credentials", message: "Invalid login credentials" });

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json(405, { error: "method_not_allowed" });
  }

  let username: unknown;
  let password: unknown;
  try {
    ({ username, password } = await req.json());
  } catch {
    return json(400, { error: "invalid_request" });
  }
  if (typeof username !== "string" || typeof password !== "string" || password.length === 0) {
    return json(400, { error: "invalid_request" });
  }

  const canonical = username.trim().toLowerCase();
  if (!USERNAME_PATTERN.test(canonical)) {
    return invalidCredentials();
  }

  const url = Deno.env.get("SUPABASE_URL")!;
  const clientOptions = { auth: { persistSession: false, autoRefreshToken: false } };

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, clientOptions);
  const { data: profile, error: lookupError } = await admin
    .from("users")
    .select("email")
    .eq("username", canonical)
    .maybeSingle();

  if (lookupError) {
    console.error("username lookup failed", lookupError.message);
    return json(500, { error: "server_error" });
  }
  if (!profile) {
    return invalidCredentials();
  }

  // Sign in with the anon key, exactly as the app would, so Auth applies its normal checks.
  const anon = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, clientOptions);
  const { data, error } = await anon.auth.signInWithPassword({
    email: profile.email,
    password,
  });

  if (error || !data.session) {
    if (error?.status === 429) {
      return json(429, { error: "over_request_rate_limit" });
    }
    if (error?.code === "email_not_confirmed") {
      return json(400, { error: "email_not_confirmed" });
    }
    return invalidCredentials();
  }

  const { access_token, refresh_token, expires_in, token_type } = data.session;
  return json(200, { access_token, refresh_token, expires_in, token_type });
});
