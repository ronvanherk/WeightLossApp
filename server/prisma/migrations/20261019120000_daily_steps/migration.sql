-- CreateTable
CREATE TABLE "daily_steps" (
    "id" SERIAL NOT NULL,
    "user_id" INTEGER NOT NULL,
    "date" DATE NOT NULL,
    "steps" INTEGER NOT NULL,

    CONSTRAINT "daily_steps_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "daily_steps_user_id_date_key" ON "daily_steps"("user_id", "date");

-- AddForeignKey
ALTER TABLE "daily_steps" ADD CONSTRAINT "daily_steps_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "app_user"("id") ON DELETE CASCADE ON UPDATE CASCADE;
