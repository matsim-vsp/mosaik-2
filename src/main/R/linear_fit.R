library(tidyverse)
library(dplyr)
library(broom)
library(ggpointdensity)


# read palm data
print("read palm data")
palm_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/no-outlier-NO2.xyt.csv")

# read matsim smoothing data
print("read matsim data")
matsim_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.outpute_emission_raster_NO2_r50.xyt.csv")
# join the data on time, x, y so that we get the following tibble | time | x | y | palm-value | matsim-value |
joined <- palm_data %>%
  inner_join(matsim_data, by = c("x", "y", "time"), suffix = c("-palm", "-matsim"))
# make scatter plots by time slice
plot <- ggplot(data = joined, mapping = aes(x = `value-matsim`, y = `value-palm`)) +
  geom_point(alpha = 0.5, shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 0.0002) +
  facet_wrap(vars(time))
ggsave(plot = plot, filename = "scatter.png", width = 18, height = 9)
plot

# calculate linear fit for each time slice
fitted_models <- joined %>%
  group_by(time) %>%
  do(model = lm(`value-matsim` ~ `value-palm`, data = .))
fitted_models$model
# apply linear fit to matsim values by time step
# https://stackoverflow.com/questions/1169539/linear-regression-and-group-by-in-r seems to be what I want


print("starting to read csv")
csv_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/linear-fit-PM10.csv")
#csv_data <- head(csv_data, 100)
csv_data

csv_data %>%
  group_by(time) %>%
  group_map(~cor(.x$matsim, .x$palm))

csv_data <- csv_data %>%
  filter(palm < 0.00001 & matsim < 0.01)

for (time in unique(csv_data$time)) {
  print(time)
  filtered <- filter(csv_data, time == time)
  print(cor(filtered$matsim, filtered$palm))
}

plot <- ggplot(data = csv_data, mapping = aes(x = matsim, y = palm)) +
  ##geom_point(pch='.') +
  geom_point(alpha = 0.05, shape = ".") +
  #geom_pointdensity(adjust = 0.0001) +
  facet_wrap(vars(time))
ggsave(plot = plot, filename = "scatter.png", width = 32, height = 18)
plot


summary <- summarize(csv_data)
summary

print("starting linear regression")
linear_model <- lm(palm ~ matsim, data = csv_data)
lm_summary <- summary(linear_model)
lm_summary

intercept <- lm_summary$coefficients["(Intercept)", "Estimate"]
grade <- lm_summary$coefficients["matsim", "Estimate"]
intercept
grade

print("starting linear regression with quadratic fitting")
quad_model <- lm(palm ~ poly(matsim, 2, raw = TRUE), data = csv_data)
quad_summary <- summary(quad_model)
quad_summary

quad_c <- quad_model$coefficients["(Intercept)"]
quad_c
quad_b <- quad_model$coefficients["poly(matsim, 2, raw = TRUE)1"]
quad_b
quad_a <- quad_model$coefficients["poly(matsim, 2, raw = TRUE)2"]
quad_a


print("plotting data")
plot <- ggplot(data = csv_data, mapping = aes(x = matsim, y = palm)) +
  geom_point(pch = '.') +
  stat_function(fun = function(x) intercept + x * grade, color = "red", size = 1) +
  stat_function(fun = function(x) quad_a * x * x + quad_b * x + quad_c, color = "blue", size = 1)
print("finished plot")

print("saving plot")
ggsave(plot = plot, filename = "pm10.png", width = 16, height = 9)