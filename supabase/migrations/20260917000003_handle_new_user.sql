-- DATAra — Phase C2: profile row creation on sign-up
--
-- Closes the open D1 item "initial user record creation in Supabase on sign-up".
-- Doing it in a trigger rather than a client-side insert means the profile row cannot
-- be missing when the client crashes or loses connectivity between sign-up and insert.
-- The client only needs to update `name` afterwards.

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.users (id, email, name)
  values (new.id, new.email, new.raw_user_meta_data ->> 'name')
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
