-- Quick entries: no food, a name and typed-in values.
ALTER TABLE "diary_entry" ALTER COLUMN "food_id" DROP NOT NULL;
ALTER TABLE "diary_entry" ADD COLUMN "name" TEXT;
ALTER TABLE "diary_entry" ADD CONSTRAINT "diary_entry_food_or_name" CHECK ("food_id" IS NOT NULL OR "name" IS NOT NULL);

-- Snapshot of fiber, sugar, saturated fat and salt; existing entries from the foods as they are now.
ALTER TABLE "diary_entry" ADD COLUMN "fiber" DOUBLE PRECISION,
    ADD COLUMN "sugar" DOUBLE PRECISION,
    ADD COLUMN "saturated_fat" DOUBLE PRECISION,
    ADD COLUMN "salt" DOUBLE PRECISION;
UPDATE "diary_entry" d SET
    "fiber" = round((f."fiber" * d."grams" / 100)::numeric, 1),
    "sugar" = round((f."sugar" * d."grams" / 100)::numeric, 1),
    "saturated_fat" = round((f."saturated_fat" * d."grams" / 100)::numeric, 1),
    "salt" = round((f."salt" * d."grams" / 100)::numeric, 2)
FROM "food" f WHERE f."id" = d."food_id";

-- Household: who may put a meal in whose diary.
ALTER TABLE "app_user" ADD COLUMN "household_id" INTEGER;

-- CreateTable
CREATE TABLE "saved_meal" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "name" TEXT NOT NULL,
    "items" JSONB NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "saved_meal_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "saved_meal_user_id_idx" ON "saved_meal"("user_id");

-- AddForeignKey
ALTER TABLE "saved_meal" ADD CONSTRAINT "saved_meal_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;
