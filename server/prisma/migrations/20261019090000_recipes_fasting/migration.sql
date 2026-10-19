-- AlterEnum
ALTER TYPE "FoodSource" ADD VALUE 'RECIPE';

-- CreateTable
CREATE TABLE "fast" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "started_at" TIMESTAMP(3) NOT NULL,
    "ended_at" TIMESTAMP(3),
    "goal_hours" INTEGER NOT NULL,

    CONSTRAINT "fast_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "fast_user_id_started_at_idx" ON "fast"("user_id", "started_at");

-- AddForeignKey
ALTER TABLE "fast" ADD CONSTRAINT "fast_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;
