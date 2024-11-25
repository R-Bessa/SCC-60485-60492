DROP TABLE IF EXISTS public.users CASCADE;
CREATE TABLE public.users(userId text PRIMARY KEY, pwd text, email text, displayName text);
CREATE INDEX idx_userId ON public.users(userId);

DROP TABLE IF EXISTS public.shorts CASCADE;
CREATE TABLE public.shorts(shortId text PRIMARY KEY, ownerId text REFERENCES users(userId) ON DELETE CASCADE , blobUrl text, timestamp bigint, totalLikes integer, views integer);
CREATE INDEX idx_shortId ON public.shorts(shortId);

DROP TABLE IF EXISTS public.following CASCADE;
CREATE TABLE public.following(id text PRIMARY KEY, follower text REFERENCES users(userId) ON DELETE CASCADE, followee text REFERENCES users(userId) ON DELETE CASCADE);
CREATE INDEX idx_follwingId ON public.following(id);

DROP TABLE IF EXISTS public.likes;
CREATE TABLE public.likes(id text PRIMARY KEY, userId text REFERENCES users(userId) ON DELETE CASCADE, shortId text REFERENCES shorts(shortId) ON DELETE CASCADE, ownerId text REFERENCES users(userId) ON DELETE CASCADE);
CREATE INDEX idx_likeId ON public.likes(id);



