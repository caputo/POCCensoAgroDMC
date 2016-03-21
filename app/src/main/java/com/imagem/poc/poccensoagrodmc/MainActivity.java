package com.imagem.poc.poccensoagrodmc;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISLocalTiledLayer;
import com.esri.android.runtime.ArcGISRuntime;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geodatabase.ShapefileFeatureTable;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polygon;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.FeatureResult;
import com.esri.core.map.Graphic;
import com.esri.core.renderer.SimpleRenderer;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.tasks.SpatialRelationship;
import com.esri.core.tasks.ags.query.Query;
import com.esri.core.tasks.query.QueryParameters;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MainActivity extends Activity {

    public FrameLayout mViewContainer;
    private Point p1;
    private Point p2;
    final private double selectionScale = 18055.954822;
    MapView mapView = null;
    Polygon initialExtent = null;
    private Toolbar toolbar;

    final private String basemapPath = "/storage/extSdCard/data/RiodeJaneiro/basemap/basemap.tpk";
    final private String gdbPath = "/storage/extSdCard/data/RiodeJaneiro/gdb/poc.geodatabase";
    final private String shpPath = "/storage/extSdCard/data/RiodeJaneiro/shp/";

    private List<GeodatabaseFeatureTable> gdbTables = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ArcGISRuntime.setClientId("gZK3c64UFVTUmPcI");

        ArcGISLocalTiledLayer basemap = new ArcGISLocalTiledLayer(basemapPath);
        initialExtent = basemap.getExtent();
        FeatureLayer layerShpPonto = null;
        FeatureLayer layerShpLinha = null;

        try {
            Geodatabase localGdb = new Geodatabase(gdbPath);
            gdbTables = localGdb.getGeodatabaseTables();

            ShapefileFeatureTable shpPonto = new ShapefileFeatureTable(
                    shpPath + "VLT_Estacoes.shp");
            layerShpPonto = new FeatureLayer(shpPonto);
            layerShpPonto.setRenderer(
                    new SimpleRenderer(
                            new SimpleMarkerSymbol(
                                    Color.WHITE, 10, SimpleMarkerSymbol.STYLE.TRIANGLE)));

            ShapefileFeatureTable shpLinha = new ShapefileFeatureTable(
                    shpPath + "VLT_Percurso.shp");
            layerShpLinha = new FeatureLayer(shpLinha);
            layerShpLinha.setRenderer(
                    new SimpleRenderer(
                            new SimpleLineSymbol(
                                    Color.WHITE, (float)1.5, SimpleLineSymbol.STYLE.SOLID)));

        } catch (FileNotFoundException e) {
            Toast.makeText(getApplicationContext(),
                    "Arquivo shp não encontrado no SD Card", Toast.LENGTH_LONG).show();
        }

        mapView = new MapView(
                MainActivity.this, basemap.getSpatialReference(), basemap.getFullExtent());
        
        mapView.addLayer(basemap);
        mapView.addLayer(new FeatureLayer(gdbTables.get(0)));
        mapView.addLayer(new FeatureLayer(gdbTables.get(1)));
        mapView.addLayer(layerShpPonto);
        mapView.addLayer(layerShpLinha);

        mViewContainer = (FrameLayout) findViewById(R.id.main_activity_view_container);
        mViewContainer.addView(mapView);

        // Carga de Endereços na lista e no mapa
        final Endereco enderecos[] = loadEnderecos();
        loadGraphiLayerFromArray(enderecos);
        loadListFromArray(enderecos);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.map_tools, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.zoomToFullExtent:
                mapView.setExtent(initialExtent);
                return true;

            case R.id.zoomIn:
                mapView.zoomin();
                return true;

            case R.id.zoomOut:
                mapView.zoomout();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }


    private void loadGraphiLayerFromArray(Endereco[] enderecos) {

        final GraphicsLayer graphicsLayer = new GraphicsLayer();

        final SimpleMarkerSymbol dentroAOI = new SimpleMarkerSymbol(
                Color.GREEN, 16, SimpleMarkerSymbol.STYLE.CIRCLE);

        final SimpleMarkerSymbol foraAOI = new SimpleMarkerSymbol(
                Color.RED, 16, SimpleMarkerSymbol.STYLE.CIRCLE);

        for (final Endereco e : enderecos) {

            SimpleMarkerSymbol simpleMarker = null;
            /*
            Executar uma querie verificado se e.getPoint() está dentro ou fora da área de
            interesse "COD_MUN_CEP5 = '330455 20211'", se está associa o detroAOI marker
            senão associa o foraAOI marker

            A consulta é executada sobre os setores censitários pois a lista de enderecos
            não é uma FeatureTable, é apenas uma lista de pontos com atributos em um vetor
            apresentados em um Graphic Layer
            */
            QueryParameters setorOI = new QueryParameters();
            setorOI.setWhere("COD_MUN_CEP5 = '330455 20211'");
            setorOI.setOutFields(new String[]{"COD_MUN_CEP5"});
            setorOI.setGeometry(e.getPoint());
            setorOI.setSpatialRelationship(SpatialRelationship.WITHIN);

            GeodatabaseFeatureTable gdbFeatureTable = gdbTables.get(0);
            Future resultFuture = gdbFeatureTable.queryFeatures(
                    setorOI, new CallbackListener<FeatureResult>() {

                public void onError(Throwable e) {
                    e.printStackTrace();
                }

                public void onCallback(FeatureResult featureIterator) {
                    if (featureIterator.featureCount() > 0) {
                        graphicsLayer.addGraphic(new Graphic(e.getPoint(), dentroAOI));;
                    } else {
                        graphicsLayer.addGraphic(new Graphic(e.getPoint(), foraAOI));
                    }

                }
            });

        }

        mapView.addLayer(graphicsLayer);
    }

    private void loadListFromArray(final Endereco[] enderecos) {

        ListView lvEnderecos = (ListView) findViewById(R.id.listViewEnderecos);
        List<String> alEnderecos = new ArrayList<String>();
        for (Endereco e : enderecos) {
            alEnderecos.add(e.getNome());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                alEnderecos);
/*
        EnderecoAdapter adapter = new EnderecoAdapter(
                this,
                R.layout.listview_item_row,
                enderecos
        );
*/
        lvEnderecos.setAdapter(adapter);

        lvEnderecos.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {

                //Endereco e = (Endereco) parent.getItemAtPosition(position);
                //mapView.zoomToScale(e.getPoint(), selectionScale);

                mapView.zoomToScale(enderecos[position].getPoint(), selectionScale);
            }

        });
    }


    private Endereco[] loadEnderecos() {
        return new Endereco[]
        {
                new Endereco("Praça da República", new Point(-4807724.667, -2620682.984)),
                new Endereco("Aeroporto Santos Dummont", new Point(-4804787.787, -2621778.362))
        };

    }
}