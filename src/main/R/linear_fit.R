library(modelr)
library(tidyverse)

# read palm data
print("read palm data")
palm_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-no-outliers.xyt.csv",
                      col_select = c(time, x, y, NO, NO2, PM10))
palm_data <- mutate(palm_data, NOx = NO + NO2)

# read matsim smoothing data
print("read matsim data")
matsim_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.output_smoothed_rastered.xyt.csv")


# join the data on time, x, y so that we get the following tibble | time | x | y | palm | matsim |
joined <- palm_data %>%
  inner_join(matsim_data, by = c("x", "y", "time"), suffix = c("_palm", "_matsim"))

joined_nox <- joined %>%
  select(time, x, y, NOx_matsim, NOx_palm)
joined_pm <- joined %>%
  select(time, x, y, PM10_matsim, PM10_palm)

# make scatter plots by time slice
plot <- ggplot(data = joined_nox, mapping = aes(x = NOx_matsim, y = NOx_palm)) +
  geom_point(alpha = 0.5, shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 0.0005) +
  xlim(0, 0.01) +
  ggtitle("MATSim NOx Concentrations to PALM NOx Concentrations") +
  facet_wrap(vars(time))
ggsave(plot, filename = "matsim-palm-nox-concentrations.png", height = 9, width = 16)

# make scatter plots by time slice
plot <- ggplot(data = joined_pm, mapping = aes(x = PM10_matsim, y = PM10_palm)) +
  geom_point(alpha = 0.5, shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 2.5e-5) +
  xlim(0, 0.0005) +
  ggtitle("MATSim PM10 Concentrations to PALM PM10 Concentrations") +
  facet_wrap(vars(time))
ggsave(plot, filename = "matsim-palm-pm10-concentrations.png", height = 9, width = 16)


estimate_regression <- function(df) {
  lm(palm ~ matsim, data = df)
}

# calculate linear fit for each time slice according to https://r4ds.had.co.nz/many-models.html
nested <- joined %>%
  #filter(time >= 21600 & time < 36000) %>%
  group_by(time) %>%
  nest() %>%
  mutate(model = map(data, estimate_regression)) %>%
  mutate(pred = map2(data, model, modelr::add_predictions)) %>%
  mutate(residuals = map2(data, model, modelr::add_residuals))

nested_matsim <- matsim_data %>%
  #filter(time >= 21600 & time < 36000) %>%
  mutate(matsim = value, .keep = "unused") %>%
  group_by(time) %>%
  nest()

selected_models <- nested %>%
  select(time, model)

matsim_with_pred <- selected_models %>%
  left_join(nested_matsim, by = "time") %>%
  mutate(pred = map2(data, model, modelr::add_predictions)) %>%
  unnest(pred) %>%
  select(time, x, y, pred) %>%
  mutate(value = pred, .keep = "unused")

residuals <- nested %>%
  unnest(residuals)

predicted <- nested %>%
  unnest(pred)

write_csv(matsim_with_pred, file = "./matsim-with-pred.xyt.csv")

plot <- ggplot(data = predicted, mapping = aes(x = matsim, y = pred)) +
  geom_point(shape = ".") +
  ggtitle("MATSim Concentrations to predicted") +
  facet_wrap(vars(hour))
ggsave(plot, filename = "matsim-predict.png")

plot <- ggplot(data = predicted, mapping = aes(x = palm, y = pred)) +
  geom_point(shape = ".") +
  ggtitle("PALM Concentrations to predicted") +
  facet_wrap(vars(hour))
ggsave(plot, filename = "palm-predict.png")

plot <- ggplot(data = residuals, mapping = aes(x = matsim, y = resid)) +
  geom_point(shape = ".") +
  ggtitle("MATSim Concentrations to residuals") +
  facet_wrap(vars(hour))
ggsave(plot, filename = "matsim-resid.png")

map_plot <- ggplot(data = matsim_with_pred, aes(x, y)) +
  geom_raster(aes(fill = value)) +
  ggtitle("Predicted Concentrations") +
  facet_wrap(vars(time))
ggsave(map_plot, filename = "predicted-maps.png", height = 9, width = 16)

# make scatter plots by time slice
plot <- ggplot(data = joined, mapping = aes(x = matsim, y = palm)) +
  geom_point(alpha = 0.5, shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 0.0002) +
  ggtitle("MATSim Concentrations to PALM Concentrations") +
  facet_wrap(vars(time))
ggsave(plot, filename = "matsim-palm-concentrations.png")