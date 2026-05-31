/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.data.db

import androidx.room.TypeConverter
import app.altio.service.domain.job.JobStatus
import app.altio.service.domain.job.JobType

class JobTypeConverter {
  @TypeConverter fun fromJobType(value: JobType): String = value.name

  @TypeConverter fun toJobType(value: String): JobType = JobType.valueOf(value)

  @TypeConverter fun fromJobStatus(value: JobStatus): String = value.name

  @TypeConverter fun toJobStatus(value: String): JobStatus = JobStatus.valueOf(value)
}
