package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CreatePlanUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val createPlan = CreatePlanUseCase(plans, templates, FakeTimeProvider(), dispatchers)

    @Test
    fun `a blank plan is one active plan with one default template`() = runTest {
        val templateId = createPlan("My routine", "Weekday").getOrNull()

        assertThat(plans.plans.value.single().isActive).isTrue()
        assertThat(templates.templates.value.single().id).isEqualTo(templateId)
        assertThat(templates.templates.value.single().isDefault).isTrue()
    }

    @Test
    fun `a second call reuses the plan rather than making another`() = runTest {
        val first = createPlan("My routine", "Weekday").getOrNull()
        val second = createPlan("Other", "Other").getOrNull()

        assertThat(second).isEqualTo(first)
        assertThat(plans.plans.value).hasSize(1)
        assertThat(templates.templates.value).hasSize(1)
    }
}
