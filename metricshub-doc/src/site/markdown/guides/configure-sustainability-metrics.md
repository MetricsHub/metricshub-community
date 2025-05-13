keywords: configuration, sustainability, dashboards, Grafana, Datadog
description: How to configure MetricsHub for sustainability metrics reporting and ensure Datadog and Grafana dashboards are populated.

# Sustainability metrics

<!-- MACRO{toc|fromDepth=1|toDepth=3|id=toc} -->

**MetricsHub** enriches hardware data with **energy usage metrics**, such as `hw.host.energy`, `hw.host.heating_margin`, `hw.host.power`, to deliver an accurate assessment of a resource's carbon footprint. Visualized in user-friendly dashboards, these sustainability metrics offer a comprehensive understanding of the environmental impact of the IT infrastructure.

To ensure dashboards are properly populated, you must configure sustainability metrics in the MetricsHub configuration file.

## Configure the sustainability settings

To obtain the electricity costs and carbon footprint of your sites and the resources within them, follow these steps to set up the MetricsHub configuration file:

1. **Configure the Site:** Define the `site` attribute for your infrastructure.
2. **Configure the Sustainability Metrics:** Set up sustainability metrics, including carbon intensity, electricity cost, and Power Usage Effectiveness (PUE).

### Step 1: Configure the site

Start by configuring the `site` attribute for your infrastructure. Having the `site` property allows dashboards to report the electricity costs and carbon footprint per site (e.g., per data center). This can be done differently depending on whether you have a highly distributed or centralized infrastructure.

#### Highly distributed infrastructure

For infrastructures with multiple distributed locations, configure the `site` under the `attributes` section of each `resource group`:

```yaml
resourceGroups:
  <resource-group-name>:
    attributes:
      site: <site-name>
    resources:
      <resource-id>:
        attributes:
          host.name: <hostname>
          host.type: <type>
        <protocol-configuration>
```

**Example:**

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    resources:
      myBostonHost1:
        attributes:
          host.name: my-boston-host-01
          host.type: storage
        <protocol-configuration>
      myBostonHost2:
        attributes:
          host.name: my-boston-host-02
          host.type: storage
        <protocol-configuration>
  chicago:
    attributes:
      site: chicago
    resources:
      myChicagoHost1:
        attributes:
          host.name: my-chicago-host-01
          host.type: storage
        <protocol-configuration>
      myChicagoHost2:
        attributes:
          host.name: my-chicago-host-02
          host.type: storage
        <protocol-configuration>
```

#### Centralized infrastructure

For a centralized infrastructure, configure the `site` directly under the main `attributes` section:

```yaml
attributes:
  site: <central-site>

resources:
  myCentralHost1:
    attributes:
      host.name: my-central-host-01
      host.type: storage
    <protocol-configuration>
  myCentralHost2:
    attributes:
      host.name: my-central-host-02
      host.type: storage
    <protocol-configuration>

```

### Step 2: Configure the sustainability metrics

Next, configure the sustainability `metrics` section. Place this section either within each `resource group` for distributed infrastructures or directly in the main `metrics` section for centralized infrastructure.

#### Example for distributed infrastructure

```yaml
resourceGroups:
  boston:
    attributes:
      site: boston
    metrics:
      hw.site.carbon_intensity: 230 # in g/kWh
      hw.site.electricity_cost: 0.12 # in $/kWh
      hw.site.pue: 1.8
    resources:
      myBostonHost1:
        attributes:
          host.name: my-boston-host-01
          host.type: storage
        <protocol-configuration>
      myBostonHost2:
        attributes:
          host.name: my-boston-host-02
          host.type: storage
        <protocol-configuration>
```

#### Example for centralized infrastructure

```yaml
attributes:
  site: <central-site>
metrics:
  hw.site.carbon_intensity: 230 # in g/kWh
  hw.site.electricity_cost: 0.12 # in $/kWh
  hw.site.pue: 1.8
resources:
  myCentralHost1:
    attributes:
      host.name: my-central-host-01
      host.type: storage
    <protocol-configuration>
  myCentralHost2:
    attributes:
      host.name: my-central-host-02
      host.type: storage
    <protocol-configuration>
```

### Metric details

* `hw.site.carbon_intensity` is the **carbon intensity in grams per kiloWatthour**. This information is required to calculate the carbon emissions of your site. The carbon intensity corresponds to the amount of CO₂ emissions produced per kWh of electricity and varies depending on the country and the region where the data center is located. See the [electricityMap Web site](https://app.electricitymap.org/map) for reference.
* `hw.site.electricity_cost` is the **electricity price in the currency of your choice per kiloWattHour**. This information is required to calculate the energy cost of your site. Refer to your energy contract to know the tariff by kilowatt per hour charged by your supplier or refer to the [GlobalPetrolPrices Web site](https://www.globalpetrolprices.com/electricity_prices/). Make sure to always use the same currency for all instances of **MetricsHub** on all sites to allow cost aggregation in your dashboards that cover multiple sites.
* `hw.site.pue` is the **Power Usage Effectiveness (PUE)** of your site. By default, sites are set with a PUE of 1.8, which is the average value for typical data centers.

You can either hardcode a value or configure **MetricsHub** to retrieve that information.
