-- AlterTable
ALTER TABLE "food" ADD COLUMN     "category" TEXT,
ADD COLUMN     "details" JSONB,
ADD COLUMN     "nutrients" JSONB,
ADD COLUMN     "saturated_fat" DOUBLE PRECISION,
ADD COLUMN     "unit" TEXT NOT NULL DEFAULT 'g';

-- CreateTable
CREATE TABLE "nutrient" (
    "code" TEXT NOT NULL,
    "group_nl" TEXT NOT NULL,
    "group_en" TEXT NOT NULL,
    "name_nl" TEXT NOT NULL,
    "name_en" TEXT NOT NULL,
    "unit" TEXT NOT NULL,
    "sort_order" INTEGER NOT NULL,

    CONSTRAINT "nutrient_pkey" PRIMARY KEY ("code")
);

-- CreateTable
CREATE TABLE "food_ingredient" (
    "food_id" INTEGER NOT NULL,
    "ingredient_id" INTEGER NOT NULL,
    "share" DOUBLE PRECISION NOT NULL,

    CONSTRAINT "food_ingredient_pkey" PRIMARY KEY ("food_id","ingredient_id")
);

-- CreateTable
CREATE TABLE "water_entry" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "date" DATE NOT NULL,
    "ml" INTEGER NOT NULL,

    CONSTRAINT "water_entry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "favorite" (
    "user_id" INTEGER NOT NULL,
    "food_id" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "favorite_pkey" PRIMARY KEY ("user_id","food_id")
);

-- CreateIndex
CREATE UNIQUE INDEX "water_entry_user_id_date_key" ON "water_entry"("user_id", "date");

-- AddForeignKey
ALTER TABLE "food_ingredient" ADD CONSTRAINT "food_ingredient_food_id_fkey" FOREIGN KEY ("food_id") REFERENCES "food"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "food_ingredient" ADD CONSTRAINT "food_ingredient_ingredient_id_fkey" FOREIGN KEY ("ingredient_id") REFERENCES "food"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "water_entry" ADD CONSTRAINT "water_entry_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "favorite" ADD CONSTRAINT "favorite_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "favorite" ADD CONSTRAINT "favorite_food_id_fkey" FOREIGN KEY ("food_id") REFERENCES "food"("id") ON DELETE CASCADE ON UPDATE CASCADE;

