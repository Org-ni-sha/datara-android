-- DATAra — reference data.
-- Carrier names are matched against TelephonyManager.getSimOperatorName() in D2.
insert into public.providers (provider_name)
values ('Smart'), ('Globe'), ('DITO')
on conflict (provider_name) do nothing;

-- Catalog promos (D3) are intentionally not seeded here. They are real carrier offers
-- with real prices and allowances, so they should be entered from verified carrier
-- sources rather than invented. Insert them as rows with user_id left null, e.g.:
--
-- insert into public.promos (user_id, provider_id, promo_name, data_amount_gb, promo_price, validity_days)
-- select null, provider_id, '<promo name>', <gb>, <price>, <days>
-- from public.providers where provider_name = 'Smart';
