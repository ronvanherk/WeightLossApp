-- Own targets: macro split, a fixed kcal goal, more on the weekend.
ALTER TABLE "profile"
  ADD COLUMN "protein_pct" INTEGER NOT NULL DEFAULT 25,
  ADD COLUMN "fat_pct" INTEGER NOT NULL DEFAULT 30,
  ADD COLUMN "carbs_pct" INTEGER NOT NULL DEFAULT 45,
  ADD COLUMN "fixed_kcal" INTEGER,
  ADD COLUMN "weekend_extra_kcal" INTEGER NOT NULL DEFAULT 0,
  ADD CONSTRAINT "profile_macro_split" CHECK ("protein_pct" + "fat_pct" + "carbs_pct" = 100);

-- Portion units.
ALTER TABLE "food" ADD COLUMN "serving_label" TEXT;

CREATE TABLE "food_unit" (
    "id" SERIAL NOT NULL,
    "food_id" INTEGER NOT NULL,
    "name" TEXT NOT NULL,
    "grams" DOUBLE PRECISION NOT NULL,
    "created_by_id" INTEGER,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "food_unit_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "food_unit_food_id_name_key" ON "food_unit"("food_id", "name");
ALTER TABLE "food_unit" ADD CONSTRAINT "food_unit_food_id_fkey" FOREIGN KEY ("food_id") REFERENCES "food"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "food_unit" ADD CONSTRAINT "food_unit_created_by_id_fkey" FOREIGN KEY ("created_by_id") REFERENCES "app_user"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- Body measurements and progress photos.
CREATE TABLE "body_measurement" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "date" DATE NOT NULL,
    "waist_cm" DOUBLE PRECISION,
    "hip_cm" DOUBLE PRECISION,
    "chest_cm" DOUBLE PRECISION,
    "thigh_cm" DOUBLE PRECISION,
    "arm_cm" DOUBLE PRECISION,
    "neck_cm" DOUBLE PRECISION,
    "body_fat_pct" DOUBLE PRECISION,
    CONSTRAINT "body_measurement_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "body_measurement_user_id_date_key" ON "body_measurement"("user_id", "date");
ALTER TABLE "body_measurement" ADD CONSTRAINT "body_measurement_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "progress_photo" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "date" DATE NOT NULL,
    "file" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "progress_photo_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "progress_photo_user_id_date_idx" ON "progress_photo"("user_id", "date");
ALTER TABLE "progress_photo" ADD CONSTRAINT "progress_photo_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;
